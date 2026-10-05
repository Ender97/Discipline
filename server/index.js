require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');
const Database = require('better-sqlite3');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const multer = require('multer');

// ─────────────────────────────────────────────
// File upload setup
// ─────────────────────────────────────────────
const UPLOADS_DIR = process.env.UPLOADS_DIR || path.join(__dirname, 'uploads');
if (!fs.existsSync(UPLOADS_DIR)) fs.mkdirSync(UPLOADS_DIR, { recursive: true });

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    // Organise into subfolders by context (e.g. habits/, avatars/) and by user ID (<context>/<userId>/)
    const sub = req.params.context || 'misc';
    const userId = req.user?.id || 'anonymous';
    const dir = path.join(UPLOADS_DIR, sub, userId);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    cb(null, dir);
  },
  filename: (req, file, cb) => {
    let ext = path.extname(file.originalname).toLowerCase();
    if (!ext || ext === '.') {
      if (file.mimetype === 'image/png') ext = '.png';
      else if (file.mimetype === 'image/gif') ext = '.gif';
      else if (file.mimetype === 'image/webp') ext = '.webp';
      else ext = '.jpg';
    }
    cb(null, `${uuidv4()}${ext}`);
  }
});

const ALLOWED_MIME = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp']);
const MAX_FILE_SIZE = parseInt(process.env.MAX_FILE_SIZE_MB || '10') * 1024 * 1024;

const upload = multer({
  storage,
  limits: { fileSize: MAX_FILE_SIZE },
  fileFilter: (req, file, cb) => {
    if (ALLOWED_MIME.has(file.mimetype)) cb(null, true);
    else cb(new Error(`File type not allowed: ${file.mimetype}`));
  }
});

const JWT_SECRET = process.env.JWT_SECRET || 'CHANGE-ME-IN-PRODUCTION';
const PORT = parseInt(process.env.PORT || '3000');
const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'discipline.db');

// ─────────────────────────────────────────────
// Automatic Daily Database Backup System
// ─────────────────────────────────────────────
const BACKUPS_DIR = process.env.BACKUPS_DIR || path.join(__dirname, 'backups');
if (!fs.existsSync(BACKUPS_DIR)) fs.mkdirSync(BACKUPS_DIR, { recursive: true });

async function createDatabaseBackup(customLabel = null) {
  try {
    const todayStr = new Date().toISOString().split('T')[0];
    const label = customLabel ? `_${customLabel}` : '';
    const filename = `discipline-backup-${todayStr}${label}.db`;
    const backupPath = path.join(BACKUPS_DIR, filename);

    // better-sqlite3 built-in atomic online backup method
    await db.backup(backupPath);
    console.log(`📦 Database backup successfully created: ${filename}`);

    // Retain backups for 30 days
    cleanupOldBackups(30);

    return { success: true, filename, path: backupPath };
  } catch (err) {
    console.error('❌ Failed to create database backup:', err.message);
    return { success: false, error: err.message };
  }
}

function cleanupOldBackups(maxDays = 30) {
  try {
    const files = fs.readdirSync(BACKUPS_DIR);
    const now = Date.now();
    const maxAgeMs = maxDays * 24 * 60 * 60 * 1000;

    files.forEach(file => {
      if (file.endsWith('.db')) {
        const filePath = path.join(BACKUPS_DIR, file);
        const stats = fs.statSync(filePath);
        if (now - stats.mtimeMs > maxAgeMs) {
          fs.unlinkSync(filePath);
          console.log(`🗑️ Deleted old backup file: ${file}`);
        }
      }
    });
  } catch (err) {
    console.error('Error cleaning up old backups:', err.message);
  }
}

function listDatabaseBackups() {
  try {
    const files = fs.readdirSync(BACKUPS_DIR);
    return files
      .filter(f => f.endsWith('.db'))
      .map(f => {
        const filePath = path.join(BACKUPS_DIR, f);
        const stats = fs.statSync(filePath);
        return {
          filename: f,
          sizeBytes: stats.size,
          createdAt: stats.mtime,
          sizeMb: (stats.size / (1024 * 1024)).toFixed(2)
        };
      })
      .sort((a, b) => b.createdAt - a.createdAt);
  } catch (err) {
    return [];
  }
}

function scheduleDailyBackup() {
  // Check once on startup
  const todayStr = new Date().toISOString().split('T')[0];
  const existingBackup = fs.readdirSync(BACKUPS_DIR).find(f => f.includes(todayStr));
  if (!existingBackup) {
    createDatabaseBackup();
  }

  // Check every hour if today's backup exists
  setInterval(() => {
    const currentDateStr = new Date().toISOString().split('T')[0];
    const hasTodayBackup = fs.readdirSync(BACKUPS_DIR).some(f => f.includes(currentDateStr));
    if (!hasTodayBackup) {
      createDatabaseBackup();
    }
  }, 60 * 60 * 1000);
}

// ─────────────────────────────────────────────
// Database setup
// ─────────────────────────────────────────────
const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL'); // Better concurrent read performance
db.pragma('foreign_keys = OFF'); // Disable foreign keys during schema creation & migrations to prevent cascade drops

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY,
    username    TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role        TEXT NOT NULL CHECK(role IN ('dominant','submissive')),
    points      INTEGER DEFAULT 0,
    pair_code   TEXT UNIQUE NOT NULL,
    partner_id  TEXT REFERENCES users(id),
    avatar_url  TEXT,
    created_at  INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS habits (
    id            TEXT PRIMARY KEY,
    owner_id      TEXT NOT NULL REFERENCES users(id),
    title         TEXT NOT NULL,
    description   TEXT,
    frequency     TEXT NOT NULL,
    weekly_target_count INTEGER DEFAULT 1,
    points_reward INTEGER DEFAULT 0,
    points_penalty INTEGER DEFAULT 0,
    penalty_punishment_id TEXT,
    is_paused     INTEGER DEFAULT 0,
    requires_photo INTEGER DEFAULT 0,
    is_nsfw       INTEGER DEFAULT 0,
    is_positive   INTEGER DEFAULT 1,
    last_penalty_reset_date TEXT,
    due_date      TEXT,
    "order"       INTEGER DEFAULT 0,
    created_at    INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS habit_submissions (
    id           TEXT PRIMARY KEY,
    habit_id     TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id      TEXT NOT NULL REFERENCES users(id),
    photo_url    TEXT NOT NULL,
    status       TEXT NOT NULL CHECK(status IN ('pending','approved','rejected')),
    submitted_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS habit_completions (
    id           TEXT PRIMARY KEY,
    habit_id     TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    user_id      TEXT NOT NULL REFERENCES users(id),
    completed_at INTEGER NOT NULL,
    week_number  INTEGER
  );

  CREATE TABLE IF NOT EXISTS rewards (
    id          TEXT PRIMARY KEY,
    owner_id    TEXT NOT NULL REFERENCES users(id),
    title       TEXT NOT NULL,
    description TEXT,
    cost        INTEGER NOT NULL DEFAULT 0,
    image_url   TEXT,
    "order"     INTEGER DEFAULT 0,
    created_at  INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS reward_redemptions (
    id          TEXT PRIMARY KEY,
    reward_id   TEXT NOT NULL REFERENCES rewards(id),
    user_id     TEXT NOT NULL REFERENCES users(id),
    approved    INTEGER DEFAULT 0,
    redeemed_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS punishments (
    id          TEXT PRIMARY KEY,
    owner_id    TEXT NOT NULL REFERENCES users(id),
    title       TEXT NOT NULL,
    description TEXT,
    "order"     INTEGER DEFAULT 0,
    created_at  INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS monthly_checkins (
    id           TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id),
    month        TEXT NOT NULL, -- YYYY-MM
    reflections  TEXT NOT NULL,
    goal_progress TEXT NOT NULL,
    rating       INTEGER NOT NULL,
    created_at   INTEGER NOT NULL,
    UNIQUE(user_id, month)
  );

  CREATE TABLE IF NOT EXISTS punishment_assignments (
    id            TEXT PRIMARY KEY,
    punishment_id TEXT NOT NULL REFERENCES punishments(id),
    assigned_to   TEXT NOT NULL REFERENCES users(id),
    assigned_by   TEXT NOT NULL REFERENCES users(id),
    reason        TEXT,
    completed     INTEGER DEFAULT 0,
    assigned_at   INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS partnerships (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    partner_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  INTEGER NOT NULL,
    UNIQUE(user_id, partner_id)
  );

  CREATE INDEX IF NOT EXISTS idx_habits_owner ON habits(owner_id);
  CREATE INDEX IF NOT EXISTS idx_completions_habit ON habit_completions(habit_id, user_id, completed_at);
  CREATE INDEX IF NOT EXISTS idx_completions_week ON habit_completions(habit_id, user_id, week_number);
  CREATE INDEX IF NOT EXISTS idx_assignments_to ON punishment_assignments(assigned_to, completed);
  CREATE INDEX IF NOT EXISTS idx_partnerships_user ON partnerships(user_id);
`);

// Auto-migration for partnerships
try {
  db.exec(`
    INSERT OR IGNORE INTO partnerships (id, user_id, partner_id, created_at)
    SELECT lower(hex(randomblob(16))), id, partner_id, ${Date.now()}
    FROM users WHERE partner_id IS NOT NULL AND partner_id != '';
  `);
} catch (e) {
  console.log('Partnerships migration notice:', e.message);
}

// Auto-migrations: Check and add missing columns safely
try {
  const habitsCols = new Set(db.prepare("PRAGMA table_info(habits)").all().map(c => c.name));
  if (!habitsCols.has('is_paused')) {
    db.prepare('ALTER TABLE habits ADD COLUMN is_paused INTEGER DEFAULT 0').run();
  }
  if (!habitsCols.has('is_nsfw')) {
    db.prepare('ALTER TABLE habits ADD COLUMN is_nsfw INTEGER DEFAULT 0').run();
  }
  if (!habitsCols.has('due_date')) {
    db.prepare('ALTER TABLE habits ADD COLUMN due_date TEXT').run();
  }
  if (!habitsCols.has('order')) {
    db.prepare('ALTER TABLE habits ADD COLUMN "order" INTEGER DEFAULT 0').run();
  }
} catch (e) {
  console.log('Habits column migration notice:', e.message);
}

['rewards', 'punishments', 'habit_completions'].forEach(table => {
  try {
    const cols = new Set(db.prepare(`PRAGMA table_info(${table})`).all().map(c => c.name));
    if (!cols.has('order') && table !== 'habit_completions') {
      db.prepare(`ALTER TABLE ${table} ADD COLUMN "order" INTEGER DEFAULT 0`).run();
    }
    if (table === 'rewards' && !cols.has('type')) {
      db.prepare("ALTER TABLE rewards ADD COLUMN type TEXT DEFAULT 'recurring'").run();
    }
    if (table === 'habit_completions' && !cols.has('type')) {
      db.prepare("ALTER TABLE habit_completions ADD COLUMN type TEXT DEFAULT 'completion'").run();
    }
    if (!cols.has('target_user_id') && table !== 'habit_completions') {
      db.prepare(`ALTER TABLE ${table} ADD COLUMN target_user_id TEXT`).run();
    }
  } catch (e) {
    console.log(`${table} column migration notice:`, e.message);
  }
});

// Auto-migration: Remove restrictive CHECK constraint on habits table if present
try {
  const tableSql = db.prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='habits'").get()?.sql || '';
  if (tableSql.includes("CHECK(frequency IN ('daily','weekly'))") || tableSql.includes("CHECK(frequency IN ('daily', 'weekly'))")) {
    console.log('⚠️ Migrating database: Updating habits table schema to support tasks...');
    db.exec(`
      CREATE TABLE habits_temp (
        id            TEXT PRIMARY KEY,
        owner_id      TEXT NOT NULL REFERENCES users(id),
        title         TEXT NOT NULL,
        description   TEXT,
        frequency     TEXT NOT NULL,
        weekly_target_count INTEGER DEFAULT 1,
        points_reward INTEGER DEFAULT 0,
        points_penalty INTEGER DEFAULT 0,
        penalty_punishment_id TEXT,
        is_paused     INTEGER DEFAULT 0,
        requires_photo INTEGER DEFAULT 0,
        is_nsfw       INTEGER DEFAULT 0,
        is_positive   INTEGER DEFAULT 1,
        last_penalty_reset_date TEXT,
        due_date      TEXT,
        "order"       INTEGER DEFAULT 0,
        created_at    INTEGER NOT NULL
      );
      INSERT INTO habits_temp (id, owner_id, title, description, frequency, weekly_target_count, points_reward, points_penalty, penalty_punishment_id, is_paused, requires_photo, is_nsfw, is_positive, last_penalty_reset_date, due_date, "order", created_at)
        SELECT id, owner_id, title, description, frequency, weekly_target_count, points_reward, points_penalty, penalty_punishment_id, COALESCE(is_paused, 0), COALESCE(requires_photo, 0), COALESCE(is_nsfw, 0), COALESCE(is_positive, 1), last_penalty_reset_date, due_date, COALESCE("order", 0), created_at FROM habits;
      DROP TABLE habits;
      ALTER TABLE habits_temp RENAME TO habits;
      CREATE INDEX idx_habits_owner ON habits(owner_id);
    `);
    console.log('✅ Migration to task-supported schema successful.');
  }
} catch (e) {
  console.log('Migration notice:', e.message);
}

// Re-enable foreign key enforcement after migrations
db.pragma('foreign_keys = ON');

// Auto-healing and migration for old/orphan database records
function autoHealDatabase(database) {
  try {
    console.log('🔄 Running database auto-heal & migration check...');

    // Clean up any junk placeholder habits or invalid photo submissions created by recovery attempts
    try {
      database.exec(`
        DELETE FROM habit_completions WHERE habit_id IN (SELECT id FROM habits WHERE title LIKE 'Restored Habit (%');
        DELETE FROM habit_submissions WHERE habit_id IN (SELECT id FROM habits WHERE title LIKE 'Restored Habit (%');
        DELETE FROM habits WHERE title LIKE 'Restored Habit (%';
        DELETE FROM habit_submissions WHERE photo_url IS NULL OR TRIM(photo_url) = '' OR photo_url LIKE '%/undefined%' OR photo_url LIKE '%/null%' OR submitted_at < 1672531200000;
      `);
    } catch (cleanErr) {
      console.log('Cleanup notice:', cleanErr.message);
    }

    const users = database.prepare('SELECT id, role, username FROM users').all();
    if (users.length === 0) {
      console.log('ℹ️ No users in database yet. Skipping orphan re-linking.');
      return;
    }

    // Clean up orphan records from deleted users
    database.exec(`
      DELETE FROM habits WHERE owner_id NOT IN (SELECT id FROM users);
      DELETE FROM rewards WHERE owner_id NOT IN (SELECT id FROM users);
      DELETE FROM punishments WHERE owner_id NOT IN (SELECT id FROM users);
      DELETE FROM habit_completions WHERE user_id NOT IN (SELECT id FROM users);
      DELETE FROM habit_submissions WHERE user_id NOT IN (SELECT id FROM users);
    `);

    // 6. Normalize timestamps in habit_completions
    const completions = database.prepare('SELECT id, completed_at, week_number FROM habit_completions').all();
    const updateComp = database.prepare('UPDATE habit_completions SET completed_at = ?, week_number = ? WHERE id = ?');

    database.transaction(() => {
      completions.forEach(c => {
        let ms = null;
        if (typeof c.completed_at === 'number') {
          ms = c.completed_at < 1e11 ? c.completed_at * 1000 : c.completed_at;
        } else if (typeof c.completed_at === 'string') {
          const str = c.completed_at.trim();
          let parsed = Number(str);
          if (isNaN(parsed)) parsed = Date.parse(str);
          if (!isNaN(parsed)) {
            ms = parsed < 1e11 ? parsed * 1000 : parsed;
          }
        }
        if (ms) {
          const week = isoWeek(new Date(ms));
          updateComp.run(ms, week, c.id);
        }
      });
    })();

    console.log('✅ Auto-heal & migration check completed successfully.');
  } catch (err) {
    console.error('[AutoHeal] Error during database auto-healing:', err.message);
  }
}

autoHealDatabase(db);

// ─────────────────────────────────────────────
// Express setup
// ─────────────────────────────────────────────
const app = express();
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Auth middleware
function auth(req, res, next) {
  const header = req.headers['authorization'];
  const token = header && header.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Authorization required' });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    res.status(403).json({ error: 'Invalid or expired token' });
  }
}

// Helper: ISO week number
function isoWeek(date = new Date()) {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  const year = d.getUTCFullYear();
  const yearStart = new Date(Date.UTC(year, 0, 1));
  return Math.ceil(((d - yearStart) / 86400000 + 1) / 7) + year * 100;
}

function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function generatePairCode() {
  return Math.random().toString(36).substring(2, 8).toUpperCase();
}

function safeUser(u) {
  if (!u) return null;
  return {
    id: u.id, username: u.username,
    role: u.role, points: u.points,
    pairCode: u.pair_code, partnerId: u.partner_id,
    avatarUrl: u.avatar_url,
  };
}

// ─────────────────────────────────────────────
// Auth routes
// ─────────────────────────────────────────────
app.post('/auth/register', (req, res) => {
  const { username, password, role } = req.body;
  if (!username || !password || !role)
    return res.status(400).json({ error: 'username, password, role are required' });
  if (!['dominant', 'submissive'].includes(role))
    return res.status(400).json({ error: "role must be 'dominant' or 'submissive'" });

  try {
    const trimmedUsername = username.trim();
    const existing = db.prepare('SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)').get(trimmedUsername);
    if (existing) {
      return res.status(409).json({ error: 'Username already taken' });
    }

    const id = uuidv4();
    const pairCode = generatePairCode();
    const passwordHash = bcrypt.hashSync(password, 12);

    db.prepare(`
      INSERT INTO users (id, username, password_hash, role, points, pair_code, created_at)
      VALUES (?, ?, ?, ?, 0, ?, ?)
    `).run(id, trimmedUsername, passwordHash, role, pairCode, Date.now());

    const token = jwt.sign({ id, username: trimmedUsername, role }, JWT_SECRET, { expiresIn: '60d' });
    const user = db.prepare('SELECT * FROM users WHERE id = ?').get(id);
    const partners = getUserPartners(id);
    res.status(201).json({ token, user: { ...safeUser(user), partners, partner: partners[0] || null } });
  } catch (err) {
    if (err.message.includes('UNIQUE'))
      return res.status(409).json({ error: 'Username already taken' });
    console.error(err);
    res.status(500).json({ error: 'Registration failed' });
  }
});

function getUserPartners(userId) {
  const rows = db.prepare(`
    SELECT u.* FROM users u
    JOIN partnerships p ON u.id = p.partner_id
    WHERE p.user_id = ?
  `).all(userId);
  if (rows.length > 0) return rows.map(safeUser);

  const me = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(userId);
  if (me && me.partner_id) {
    const p = db.prepare('SELECT * FROM users WHERE id = ?').get(me.partner_id);
    if (p) return [safeUser(p)];
  }
  return [];
}

app.post('/auth/login', (req, res) => {
  try {
    const { username, password } = req.body;
    if (!username || !password)
      return res.status(400).json({ error: 'username and password required' });

    const trimmedUsername = username.trim();
    const user = db.prepare('SELECT * FROM users WHERE LOWER(username) = LOWER(?)').get(trimmedUsername);
    if (!user || !bcrypt.compareSync(password, user.password_hash))
      return res.status(401).json({ error: 'Invalid username or password' });

    const token = jwt.sign({ id: user.id, username: user.username, role: user.role }, JWT_SECRET, { expiresIn: '60d' });
    const partners = getUserPartners(user.id);

    res.json({ token, user: { ...safeUser(user), partners, partner: partners[0] || null } });
  } catch (err) {
    console.error('Error during login:', err);
    res.status(500).json({ error: 'Login failed due to server error' });
  }
});

// ─────────────────────────────────────────────
// User / pairing routes
// ─────────────────────────────────────────────
app.get('/users/me', auth, (req, res) => {
  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  if (!user) return res.status(404).json({ error: 'User not found' });
  const partners = getUserPartners(req.user.id);
  res.json({ ...safeUser(user), partners, partner: partners[0] || null });
});

app.post('/users/pair', auth, (req, res) => {
  const { pairCode } = req.body;
  if (!pairCode) return res.status(400).json({ error: 'pairCode required' });

  const partner = db.prepare('SELECT * FROM users WHERE pair_code = ?').get(pairCode.toUpperCase());
  if (!partner) return res.status(404).json({ error: 'Pair code not found' });
  if (partner.id === req.user.id) return res.status(400).json({ error: 'Cannot pair with yourself' });

  const existing = db.prepare('SELECT 1 FROM partnerships WHERE user_id = ? AND partner_id = ?').get(req.user.id, partner.id);
  if (existing) return res.status(400).json({ error: 'Already paired with this partner' });

  db.transaction(() => {
    db.prepare('INSERT OR IGNORE INTO partnerships (id, user_id, partner_id, created_at) VALUES (?, ?, ?, ?)').run(uuidv4(), req.user.id, partner.id, Date.now());
    db.prepare('INSERT OR IGNORE INTO partnerships (id, user_id, partner_id, created_at) VALUES (?, ?, ?, ?)').run(uuidv4(), partner.id, req.user.id, Date.now());
    db.prepare('UPDATE users SET partner_id = ? WHERE id = ?').run(partner.id, req.user.id);
    db.prepare('UPDATE users SET partner_id = ? WHERE id = ?').run(req.user.id, partner.id);
  })();

  broadcastToUsers([req.user.id, partner.id], { type: 'PAIRED' });
  res.json({ success: true, partner: safeUser(partner) });
});

app.post('/users/unpair', auth, (req, res) => {
  const partnerId = req.body.partner_id || req.body.partnerId;

  db.transaction(() => {
    if (partnerId) {
      db.prepare('DELETE FROM partnerships WHERE (user_id = ? AND partner_id = ?) OR (user_id = ? AND partner_id = ?)').run(req.user.id, partnerId, partnerId, req.user.id);
      db.prepare('UPDATE users SET partner_id = NULL WHERE id = ? AND partner_id = ?').run(req.user.id, partnerId);
      db.prepare('UPDATE users SET partner_id = NULL WHERE id = ? AND partner_id = ?').run(partnerId, req.user.id);
    } else {
      const partners = getUserPartners(req.user.id);
      db.prepare('DELETE FROM partnerships WHERE user_id = ? OR partner_id = ?').run(req.user.id, req.user.id);
      db.prepare('UPDATE users SET partner_id = NULL WHERE id = ? OR partner_id = ?').run(req.user.id, req.user.id);
      broadcastToUsers(partners.map(p => p.id), { type: 'UNPAIRED' });
    }
  })();

  res.json({ success: true });
});

// ─────────────────────────────────────────────
// Habits routes
// ─────────────────────────────────────────────
function formatCompletionDate(completedAt) {
  if (!completedAt) return null;
  const str = String(completedAt).trim();
  // If already YYYY-MM-DD
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    return str;
  }
  let ms = Number(str);
  if (isNaN(ms)) {
    ms = Date.parse(str);
    if (isNaN(ms)) return null;
  }
  if (ms < 1e11) ms *= 1000;

  const d = new Date(ms);
  if (isNaN(d.getTime())) return null;

  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function mapHabit(h, userIds, todayTs, week) {
  const isPaused = !!h.is_paused;
  let completed = false;

  const todayStr = new Date(todayTs).toISOString().split('T')[0];

  if (!h.is_positive) {
    const rows = db.prepare(
      "SELECT completed_at, COALESCE(type, 'completion') as type FROM habit_completions WHERE habit_id=?"
    ).all(h.id);

    const hasMissEntry = rows.some(r => {
      let ms = Number(r.completed_at);
      if (isNaN(ms)) ms = Date.parse(r.completed_at);
      if (ms < 1e11) ms *= 1000;
      const isToday = !isNaN(ms) ? (ms >= todayTs && ms < todayTs + 86400000) : String(r.completed_at).startsWith(todayStr);
      return isToday && r.type === 'miss';
    });

    completed = !hasMissEntry;
  } else if (h.frequency === 'daily') {
    const rows = db.prepare(
      'SELECT completed_at FROM habit_completions WHERE habit_id=?'
    ).all(h.id);

    completed = rows.some(r => {
      let ms = Number(r.completed_at);
      if (isNaN(ms)) ms = Date.parse(r.completed_at);
      if (ms < 1e11) ms *= 1000;
      if (!isNaN(ms)) {
        return ms >= todayTs && ms < todayTs + 86400000;
      }
      return String(r.completed_at).startsWith(todayStr);
    });
  } else if (h.frequency === 'weekly') {
    const rows = db.prepare(
      'SELECT completed_at, week_number FROM habit_completions WHERE habit_id=?'
    ).all(h.id);

    completed = rows.some(r => {
      if (r.week_number === week) return true;
      let ms = Number(r.completed_at);
      if (isNaN(ms)) ms = Date.parse(r.completed_at);
      if (ms < 1e11) ms *= 1000;
      if (!isNaN(ms)) {
        return isoWeek(new Date(ms)) === week;
      }
      return false;
    });
  } else if (h.frequency === 'task') {
    const row = db.prepare(
      'SELECT 1 FROM habit_completions WHERE habit_id=? LIMIT 1'
    ).get(h.id);
    completed = !!row;
  }

  return {
    id: h.id,
    ownerId: h.owner_id,
    targetUserId: h.target_user_id || null,
    title: h.title,
    description: h.description,
    frequency: h.frequency,
    weeklyTargetCount: h.weekly_target_count,
    pointsReward: h.points_reward,
    pointsPenalty: h.points_penalty,
    penaltyPunishmentId: h.penalty_punishment_id,
    lastPenaltyResetDate: h.last_penalty_reset_date,
    dueDate: h.due_date,
    isPaused: !!h.is_paused,
    requiresPhoto: !!h.requires_photo,
    isNsfw: !!h.is_nsfw,
    isPositive: !!h.is_positive,
    order: h.order || 0,
    createdAt: h.created_at,
    completed: completed
  };
}

function enrichHabits(habits, req) {
  const todayTs = startOfToday();
  const week = isoWeek();
  const ids = partnerIds(req);
  return habits.map(h => mapHabit(h, ids, todayTs, week));
}

app.get('/habits', auth, (req, res) => {
  const filter = partnershipFilter(req);
  const habits = db.prepare(`SELECT * FROM habits WHERE ${filter.sqlClause} ORDER BY "order" ASC, created_at ASC`).all(...filter.params);
  res.json(enrichHabits(habits, req));
});

// Dom can also view partner's habits
app.get('/habits/partner', auth, (req, res) => {
  const me = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(req.user.id);
  if (!me || !me.partner_id) return res.status(400).json({ error: 'Not paired' });
  const habits = db.prepare('SELECT * FROM habits WHERE owner_id = ? ORDER BY "order" ASC, created_at ASC').all(me.partner_id);
  res.json(enrichHabits(habits, req));
});

app.post('/habits', auth, (req, res) => {
  const {
    title, description, frequency,
    weekly_target_count, weeklyTargetCount,
    points_reward, pointsReward,
    points_penalty, pointsPenalty,
    penalty_punishment_id, penaltyPunishmentId,
    last_penalty_reset_date, lastPenaltyResetDate,
    due_date, dueDate,
    requires_photo, requiresPhoto,
    is_nsfw, isNsfw,
    is_positive, isPositive,
    is_paused, isPaused,
    target_user_id, targetUserId,
    order
  } = req.body;

  if (!title || !frequency) return res.status(400).json({ error: 'title and frequency required' });
  if (!['daily', 'weekly', 'task'].includes(frequency))
    return res.status(400).json({ error: "frequency must be 'daily', 'weekly', or 'task'" });

  const id = uuidv4();
  const filter = partnershipFilter(req);
  const finalTargetUserId = target_user_id || targetUserId || filter.targetPartnerId || null;
  const targetCount = weekly_target_count ?? weeklyTargetCount ?? 1;
  const reward = points_reward ?? pointsReward ?? 0;
  const penalty = points_penalty ?? pointsPenalty ?? 0;
  const pId = penalty_punishment_id ?? penaltyPunishmentId ?? null;
  const resetDate = last_penalty_reset_date ?? lastPenaltyResetDate ?? null;
  const taskDueDate = due_date ?? dueDate ?? null;
  const rPhoto = requires_photo ?? requiresPhoto ?? false;
  const nsfw = is_nsfw ?? isNsfw ?? false;
  const positive = is_positive ?? isPositive ?? true;
  const paused = is_paused ?? isPaused ?? false;
  const habitOrder = order ?? 0;

  db.prepare(`
    INSERT INTO habits (id, owner_id, target_user_id, title, description, frequency, weekly_target_count, points_reward, points_penalty, penalty_punishment_id, last_penalty_reset_date, due_date, requires_photo, is_nsfw, is_positive, is_paused, "order", created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(id, req.user.id, finalTargetUserId, title, description || null, frequency, targetCount, reward, penalty, pId, resetDate, taskDueDate, rPhoto ? 1 : 0, nsfw ? 1 : 0, positive ? 1 : 0, paused ? 1 : 0, habitOrder, Date.now());

  const habit = db.prepare('SELECT * FROM habits WHERE id = ?').get(id);

  // For a new habit, completion is always false
  const todayTs = startOfToday();
  const week = isoWeek();
  const result = mapHabit(habit, req.user.id, todayTs, week);

  notifyPartner(req.user.id, { type: 'HABIT_CREATED', habit: result });
  res.status(201).json(result);
});

app.put('/habits/:id', auth, (req, res) => {
  console.log(`PUT /habits/${req.params.id} received. body:`, JSON.stringify(req.body));
  const existing = db.prepare('SELECT * FROM habits WHERE id = ?').get(req.params.id);
  if (!existing) return res.status(404).json({ error: 'Habit not found' });

  // Only Dominant can edit habits
  if (req.user.role !== 'dominant') {
    return res.status(403).json({ error: 'Forbidden: Only dominants can edit habits' });
  }

  const {
    title, description, frequency,
    weekly_target_count, weeklyTargetCount,
    points_reward, pointsReward,
    points_penalty, pointsPenalty,
    penalty_punishment_id, penaltyPunishmentId,
    last_penalty_reset_date, lastPenaltyResetDate,
    due_date, dueDate,
    requires_photo, requiresPhoto,
    is_nsfw, isNsfw,
    is_positive, isPositive,
    is_paused, isPaused,
    order
  } = req.body;

  const targetCount = weekly_target_count ?? weeklyTargetCount ?? existing.weekly_target_count;
  const reward = points_reward ?? pointsReward ?? existing.points_reward;
  const penalty = points_penalty ?? pointsPenalty ?? existing.points_penalty;
  const pId = penalty_punishment_id !== undefined ? penalty_punishment_id : (penaltyPunishmentId !== undefined ? penaltyPunishmentId : existing.penalty_punishment_id);
  const resetDate = last_penalty_reset_date !== undefined ? last_penalty_reset_date : (lastPenaltyResetDate !== undefined ? lastPenaltyResetDate : existing.last_penalty_reset_date);
  const taskDueDate = due_date !== undefined ? due_date : (dueDate !== undefined ? dueDate : existing.due_date);
  const rPhoto = requires_photo !== undefined ? requires_photo : (requiresPhoto !== undefined ? requiresPhoto : !!existing.requires_photo);
  const nsfw = is_nsfw !== undefined ? is_nsfw : (isNsfw !== undefined ? isNsfw : !!existing.is_nsfw);
  const positive = is_positive !== undefined ? is_positive : (isPositive !== undefined ? isPositive : !!existing.is_positive);
  const paused = is_paused !== undefined ? is_paused : (isPaused !== undefined ? isPaused : !!existing.is_paused);
  const habitOrder = order !== undefined ? order : existing.order;

  db.prepare(`
    UPDATE habits SET
      title = ?, description = ?, frequency = ?, weekly_target_count = ?,
      points_reward = ?, points_penalty = ?, penalty_punishment_id = ?,
      last_penalty_reset_date = ?, due_date = ?, requires_photo = ?, is_nsfw = ?, is_positive = ?, is_paused = ?, "order" = ?
    WHERE id = ?
  `).run(
    title ?? existing.title,
    description !== undefined ? description : existing.description,
    frequency ?? existing.frequency,
    targetCount,
    reward,
    penalty,
    pId,
    resetDate,
    taskDueDate,
    rPhoto ? 1 : 0,
    nsfw ? 1 : 0,
    positive ? 1 : 0,
    paused ? 1 : 0,
    habitOrder,
    req.params.id
  );

  const updated = db.prepare('SELECT * FROM habits WHERE id = ?').get(req.params.id);
  const enriched = enrichHabits([updated], req.user.id)[0];
  notifyPartner(req.user.id, { type: 'HABIT_UPDATED', habit: enriched });
  res.json(enriched);
});

app.delete('/habits/:id', auth, (req, res) => {
  const existing = db.prepare('SELECT * FROM habits WHERE id = ?').get(req.params.id);
  if (!existing) return res.status(404).json({ error: 'Habit not found' });

  if (req.user.role !== 'dominant') {
    return res.status(403).json({ error: 'Forbidden: Only dominants can delete habits' });
  }

  db.prepare('DELETE FROM habits WHERE id = ?').run(req.params.id);
  notifyPartner(req.user.id, { type: 'HABIT_DELETED', habitId: req.params.id });
  res.json({ success: true });
});

app.post('/habits/reorder', auth, (req, res) => {
  const habitIds = req.body;
  if (!Array.isArray(habitIds)) return res.status(400).json({ error: 'Array of IDs required' });
  console.log(`Reordering habits: ${habitIds.join(', ')}`);

  const reorder = db.transaction(() => {
    habitIds.forEach((id, index) => {
      db.prepare('UPDATE habits SET "order" = ? WHERE id = ?').run(index, id);
    });
  });
  reorder();

  notifyPartner(req.user.id, { type: 'HABITS_REORDERED' });
  res.json({ success: true });
});

app.post('/habits/:id/complete', auth, (req, res) => {
  const filter = partnershipFilter(req);
  const habit = db.prepare(`SELECT * FROM habits WHERE id = ? AND ${filter.sqlClause}`).get(req.params.id, ...filter.params);
  if (!habit) return res.status(404).json({ error: 'Habit not found' });

  const { date } = req.body; // Optional YYYY-MM-DD
  let targetDate;
  if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
    const [y, m, d] = date.split('-').map(Number);
    targetDate = new Date(y, m - 1, d); // Construct in local server time
  } else {
    targetDate = new Date();
  }
  targetDate.setHours(0,0,0,0);

  const todayTs = targetDate.getTime();
  const week = isoWeek(targetDate);

  // Determine who we are completing this for
  const me = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  let targetUserId = req.user.id;
  if (me.role === 'dominant') {
    targetUserId = filter.targetPartnerId || me.partner_id || req.user.id;
  }

  // Idempotency check
  if (habit.frequency === 'daily' || !habit.is_positive) {
    const done = db.prepare(
      'SELECT 1 FROM habit_completions WHERE habit_id=? AND user_id=? AND completed_at>=? AND completed_at<?'
    ).get(req.params.id, targetUserId, todayTs, todayTs + 86400000);
    if (done) return res.status(409).json({ error: 'Already completed for this date' });
  } else if (habit.frequency === 'weekly') {
    // 1. Check if ALREADY completed on this specific day
    const doneToday = db.prepare(
      'SELECT 1 FROM habit_completions WHERE habit_id=? AND user_id=? AND completed_at>=? AND completed_at<?'
    ).get(req.params.id, targetUserId, todayTs, todayTs + 86400000);
    if (doneToday) return res.status(409).json({ error: 'Already completed for this date' });

    // 2. Check if weekly target completions count has been reached for this week
    const target = Math.max(1, habit.weekly_target_count || 1);
    const countRow = db.prepare(
      'SELECT count(*) as c FROM habit_completions WHERE habit_id=? AND user_id=? AND week_number=?'
    ).get(req.params.id, targetUserId, week);
    const weeklyCount = countRow ? countRow.c : 0;
    if (weeklyCount >= target) {
      return res.status(409).json({ error: `Weekly target of ${target} completion(s) already reached for this week` });
    }
  } else if (habit.frequency === 'task') {
    const done = db.prepare(
      'SELECT 1 FROM habit_completions WHERE habit_id=? AND user_id=?'
    ).get(req.params.id, targetUserId);
    if (done) return res.status(409).json({ error: 'Task already completed' });
  }

  const complete = db.transaction(() => {
    // For past dates, we set completion time to noon of that day
    const completionTime = date ? todayTs + 43200000 : Date.now();

    db.prepare(`
      INSERT INTO habit_completions (id, habit_id, user_id, completed_at, week_number)
      VALUES (?, ?, ?, ?, ?)
    `).run(uuidv4(), req.params.id, targetUserId, completionTime, week);

    if (!habit.is_positive) {
      if (habit.points_penalty > 0) {
        db.prepare('UPDATE users SET points = MAX(0, points - ?) WHERE id = ?').run(habit.points_penalty, targetUserId);
      }
    } else {
      if (habit.points_reward > 0) {
        db.prepare('UPDATE users SET points = points + ? WHERE id = ?').run(habit.points_reward, targetUserId);
      }
    }
    return db.prepare('SELECT points FROM users WHERE id = ?').get(targetUserId).points;
  });

  const points = complete();
  notifyPartner(req.user.id, { type: 'HABIT_COMPLETED', habitId: req.params.id, userId: targetUserId, points, date });
  res.json({ success: true, points });
});

app.post('/habits/:id/uncomplete', auth, (req, res) => {
  const filter = partnershipFilter(req);
  const habit = db.prepare(`SELECT * FROM habits WHERE id = ? AND ${filter.sqlClause}`).get(req.params.id, ...filter.params);
  if (!habit) return res.status(404).json({ error: 'Habit not found' });

  const { date } = req.body; // Optional YYYY-MM-DD
  let targetDate;
  if (date && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
    const [y, m, d] = date.split('-').map(Number);
    targetDate = new Date(y, m - 1, d);
  } else {
    targetDate = new Date();
  }
  targetDate.setHours(0,0,0,0);

  const todayTs = targetDate.getTime();
  const week = isoWeek(targetDate);

  const me = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  let targetUserId = req.user.id;
  if (me.role === 'dominant') {
    targetUserId = filter.targetPartnerId || me.partner_id || req.user.id;
  }

  const undo = db.transaction(() => {
    let changes = 0;
    if (habit.frequency === 'daily' || habit.frequency === 'weekly' || !habit.is_positive) {
      // First try to delete completion/miss on that specific day
      changes = db.prepare(
        'DELETE FROM habit_completions WHERE habit_id=? AND user_id=? AND completed_at>=? AND completed_at<?'
      ).run(req.params.id, targetUserId, todayTs, todayTs + 86400000).changes;

      // For weekly habits, if no completion was found on that exact day, delete the most recent completion in that week
      if (changes === 0 && habit.frequency === 'weekly') {
        const lastInWeek = db.prepare(
          'SELECT id FROM habit_completions WHERE habit_id=? AND user_id=? AND week_number=? ORDER BY completed_at DESC LIMIT 1'
        ).get(req.params.id, targetUserId, week);
        if (lastInWeek) {
          changes = db.prepare('DELETE FROM habit_completions WHERE id=?').run(lastInWeek.id).changes;
        }
      }
    } else if (habit.frequency === 'task') {
      changes = db.prepare(
        'DELETE FROM habit_completions WHERE habit_id=?'
      ).run(req.params.id).changes;

      db.prepare('DELETE FROM habit_submissions WHERE habit_id=?').run(req.params.id);
    }

    // Also remove any pending submissions for that date
    db.prepare('DELETE FROM habit_submissions WHERE habit_id=? AND user_id=? AND submitted_at>=? AND submitted_at<?')
      .run(req.params.id, targetUserId, todayTs, todayTs + 86400000);

    if (changes > 0) {
      if (!habit.is_positive) {
        if (habit.points_penalty > 0) {
          db.prepare('UPDATE users SET points = points + ? WHERE id = ?').run(habit.points_penalty, targetUserId);
        }
      } else {
        if (habit.points_reward > 0) {
          db.prepare('UPDATE users SET points = MAX(0, points - ?) WHERE id = ?').run(habit.points_reward, targetUserId);
        }
      }
    }
    return db.prepare('SELECT points FROM users WHERE id = ?').get(targetUserId).points;
  });

  const points = undo();
  notifyPartner(req.user.id, { type: 'HABIT_UNCOMPLETED', habitId: req.params.id, userId: targetUserId, points, date });
  res.json({ success: true, points });
});

app.post('/habits/:id/reset-task', auth, (req, res) => {
  const filter = partnershipFilter(req);
  const habit = db.prepare(`SELECT * FROM habits WHERE id = ? AND ${filter.sqlClause}`).get(req.params.id, ...filter.params);
  if (!habit) return res.status(404).json({ error: 'Habit not found' });

  const me = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  let targetUserId = req.user.id;
  if (me.role === 'dominant') {
    targetUserId = filter.targetPartnerId || me.partner_id || req.user.id;
  }

  const reset = db.transaction(() => {
    // Delete completions for this task without deducting points
    db.prepare('DELETE FROM habit_completions WHERE habit_id=? AND user_id=?')
      .run(req.params.id, targetUserId);

    // Also remove any pending submissions for this task
    db.prepare('DELETE FROM habit_submissions WHERE habit_id=? AND user_id=?')
      .run(req.params.id, targetUserId);

    return db.prepare('SELECT points FROM users WHERE id = ?').get(targetUserId).points;
  });

  const points = reset();
  notifyPartner(req.user.id, { type: 'HABIT_UNCOMPLETED', habitId: req.params.id, userId: targetUserId, points });
  res.json({ success: true, points });
});

function formatDateLocal(ts) {
  return formatCompletionDate(ts) || new Date().toISOString().split('T')[0];
}

app.get('/habits/submissions/pending', auth, (req, res) => {
  const filter = partnershipFilter(req);

  const submissions = db.prepare(`
    SELECT hs.*, h.title as habitTitle, h.is_nsfw as isNsfw, u.username
    FROM habit_submissions hs
    JOIN habits h ON hs.habit_id = h.id
    JOIN users u ON hs.user_id = u.id
    WHERE h.id IN (SELECT id FROM habits WHERE ${filter.sqlClause})
      AND hs.status = 'pending'
      AND hs.photo_url IS NOT NULL
      AND TRIM(hs.photo_url) != ''
      AND hs.photo_url NOT LIKE '%/undefined%'
      AND hs.photo_url NOT LIKE '%/null%'
      AND hs.submitted_at > 1672531200000
    ORDER BY hs.submitted_at DESC
  `).all(...filter.params);

  res.json(submissions.map(s => ({
    id: s.id,
    habitId: s.habit_id,
    userId: s.user_id,
    username: s.username,
    habitTitle: s.habitTitle,
    photoUrl: s.photo_url,
    status: s.status,
    submittedAt: s.submitted_at,
    date: formatDateLocal(s.submitted_at),
    isNsfw: !!s.isNsfw
  })));
});

app.get('/habits/submissions/all', auth, (req, res) => {
  const filter = partnershipFilter(req);

  const submissions = db.prepare(`
    SELECT hs.*, h.title as habitTitle, h.is_nsfw as isNsfw, u.username
    FROM habit_submissions hs
    JOIN habits h ON hs.habit_id = h.id
    JOIN users u ON hs.user_id = u.id
    WHERE h.id IN (SELECT id FROM habits WHERE ${filter.sqlClause})
      AND hs.photo_url IS NOT NULL
      AND TRIM(hs.photo_url) != ''
      AND hs.photo_url NOT LIKE '%/undefined%'
      AND hs.photo_url NOT LIKE '%/null%'
      AND hs.submitted_at > 1672531200000
    ORDER BY hs.submitted_at DESC
  `).all(...filter.params);

  res.json(submissions.map(s => ({
    id: s.id,
    habitId: s.habit_id,
    userId: s.user_id,
    username: s.username,
    habitTitle: s.habitTitle,
    photoUrl: s.photo_url,
    status: s.status,
    submittedAt: s.submitted_at,
    date: formatDateLocal(s.submitted_at),
    isNsfw: !!s.isNsfw
  })));
});

app.post('/habits/:id/submit', auth, (req, res) => {
  const ids = partnerIds(req.user.id);
  const placeholders = ids.map(() => '?').join(',');
  const habit = db.prepare(`SELECT * FROM habits WHERE id = ? AND owner_id IN (${placeholders})`).get(req.params.id, ...ids);
  if (!habit) return res.status(404).json({ error: 'Habit not found' });

  const photoUrl = req.body.photo_url || req.body.photoUrl || req.query.photoUrl;
  if (!photoUrl) return res.status(400).json({ error: 'photoUrl required' });

  const dateStr = req.body.date || req.query.date;
  let submittedAt = Date.now();
  if (dateStr && /^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
    const [y, m, d] = dateStr.split('-').map(Number);
    submittedAt = new Date(y, m - 1, d, 12, 0, 0, 0).getTime();
  }

  const id = uuidv4();
  db.prepare(`
    INSERT INTO habit_submissions (id, habit_id, user_id, photo_url, status, submitted_at)
    VALUES (?, ?, ?, ?, 'pending', ?)
  `).run(id, req.params.id, req.user.id, photoUrl, submittedAt);

  notifyPartner(req.user.id, {
    type: 'HABIT_SUBMITTED',
    submissionId: id,
    habitId: habit.id,
    habitTitle: habit.title,
    username: req.user.username
  });
  res.json({ success: true });
});

app.post('/habits/submissions/:id/approve', auth, (req, res) => {
  const sub = db.prepare('SELECT * FROM habit_submissions WHERE id = ?').get(req.params.id);
  if (!sub) return res.status(404).json({ error: 'Submission not found' });

  const ids = partnerIds(req);
  const habit = db.prepare('SELECT owner_id, points_reward, title FROM habits WHERE id = ?').get(sub.habit_id);
  const isHabitOwner = habit && ids.includes(habit.owner_id);
  const isCreatorOrPartner = ids.includes(sub.user_id) || sub.user_id === req.user.id;
  const isDominant = req.user.role === 'dominant';

  if (!isCreatorOrPartner && !isHabitOwner && !isDominant) {
    return res.status(403).json({ error: 'Forbidden: Not authorized to approve submission' });
  }

  // Determine completion time from request date parameter or submission date
  const dateStr = req.body?.date || req.query?.date;
  let completionTime = sub.submitted_at;
  if (dateStr && /^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
    const [y, m, d] = dateStr.split('-').map(Number);
    completionTime = new Date(y, m - 1, d, 12, 0, 0, 0).getTime();
  }
  const completionDate = new Date(completionTime);
  const weekNum = isoWeek(completionDate);

  const approve = db.transaction(() => {
    db.prepare("UPDATE habit_submissions SET status = 'approved' WHERE id = ?").run(req.params.id);
    db.prepare(`
      INSERT INTO habit_completions (id, habit_id, user_id, completed_at, week_number)
      VALUES (?, ?, ?, ?, ?)
    `).run(uuidv4(), sub.habit_id, sub.user_id, completionTime, weekNum);

    if (habit && habit.points_reward > 0) {
      db.prepare('UPDATE users SET points = points + ? WHERE id = ?').run(habit.points_reward, sub.user_id);
    }
    return db.prepare('SELECT points FROM users WHERE id = ?').get(sub.user_id)?.points || 0;
  });

  const points = approve();
  const dateFormatted = `${completionDate.getFullYear()}-${String(completionDate.getMonth() + 1).padStart(2, '0')}-${String(completionDate.getDate()).padStart(2, '0')}`;
  send(sub.user_id, { type: 'SUBMISSION_APPROVED', habitId: sub.habit_id, habitTitle: habit ? habit.title : '', points, date: dateFormatted });
  res.json({ success: true, points });
});

app.post('/habits/submissions/:id/reject', auth, (req, res) => {
  const sub = db.prepare('SELECT * FROM habit_submissions WHERE id = ?').get(req.params.id);
  if (!sub) return res.status(404).json({ error: 'Submission not found' });

  const ids = partnerIds(req);
  const habit = db.prepare('SELECT owner_id, title FROM habits WHERE id = ?').get(sub.habit_id);
  const isHabitOwner = habit && ids.includes(habit.owner_id);
  const isCreatorOrPartner = ids.includes(sub.user_id) || sub.user_id === req.user.id;
  const isDominant = req.user.role === 'dominant';

  if (!isCreatorOrPartner && !isHabitOwner && !isDominant) {
    return res.status(403).json({ error: 'Forbidden: Not authorized to reject submission' });
  }

  db.prepare("UPDATE habit_submissions SET status = 'rejected' WHERE id = ?").run(req.params.id);
  send(sub.user_id, { type: 'SUBMISSION_REJECTED', habitId: sub.habit_id, habitTitle: habit ? habit.title : '' });
  res.json({ success: true });
});

app.delete('/habits/submissions/:id', auth, (req, res) => {
  const sub = db.prepare('SELECT * FROM habit_submissions WHERE id = ?').get(req.params.id);
  if (!sub) {
    return res.json({ success: true });
  }

  const ids = partnerIds(req);
  const habit = db.prepare('SELECT owner_id FROM habits WHERE id = ?').get(sub.habit_id);
  const isHabitOwner = habit && ids.includes(habit.owner_id);
  const isCreatorOrPartner = ids.includes(sub.user_id) || sub.user_id === req.user.id;
  const isDominant = req.user.role === 'dominant';

  if (!isCreatorOrPartner && !isHabitOwner && !isDominant) {
    return res.status(403).json({ error: 'Forbidden: Not authorized to delete submission' });
  }

  db.prepare('DELETE FROM habit_submissions WHERE id = ?').run(req.params.id);
  notifyPartner(req.user.id, { type: 'SUBMISSION_DELETED', submissionId: req.params.id });
  res.json({ success: true });
});

// ─────────────────────────────────────────────
// Check-in routes
// ─────────────────────────────────────────────
app.get('/checkins', auth, (req, res) => {
  const ids = partnerIds(req);
  const placeholders = ids.map(() => '?').join(',');
  const rows = db.prepare(`
    SELECT mc.*, u.username
    FROM monthly_checkins mc
    JOIN users u ON mc.user_id = u.id
    WHERE mc.user_id IN (${placeholders})
    ORDER BY mc.month DESC, mc.created_at DESC
  `).all(...ids);
  res.json(rows.map(r => ({
    ...r,
    goalProgress: r.goal_progress,
    createdAt: r.created_at
  })));
});

app.post('/checkins', auth, (req, res) => {
  const { month, reflections, rating } = req.body;
  const goalProgress = req.body.goal_progress ?? req.body.goalProgress;

  if (!month || !reflections || !goalProgress || rating === undefined)
    return res.status(400).json({ error: 'month, reflections, goalProgress, rating required' });

  try {
    const id = uuidv4();
    db.prepare(`
      INSERT INTO monthly_checkins (id, user_id, month, reflections, goal_progress, rating, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(id, req.user.id, month, reflections, goalProgress, Number(rating), Date.now());

    const result = db.prepare('SELECT mc.*, u.username FROM monthly_checkins mc JOIN users u ON mc.user_id = u.id WHERE mc.id = ?').get(id);
    notifyPartner(req.user.id, { type: 'CHECKIN_SUBMITTED', checkIn: result });
    res.status(201).json({
      ...result,
      goalProgress: result.goal_progress,
      createdAt: result.created_at
    });
  } catch (err) {
    if (err.message.includes('UNIQUE'))
      return res.status(409).json({ error: 'Check-in already submitted for this month' });
    console.error(err);
    res.status(500).json({ error: 'Failed to submit check-in' });
  }
});

// ─────────────────────────────────────────────
// Rewards routes
// ─────────────────────────────────────────────
function partnerIds(req) {
  const userId = typeof req === 'string' ? req : req?.user?.id;
  if (!userId) return [];

  const headerPartnerId = (typeof req === 'object' && req?.headers) ? (req.headers['x-partner-id'] || req.query?.partnerId) : null;

  if (headerPartnerId && headerPartnerId !== userId) {
    const p = db.prepare('SELECT id FROM users WHERE id = ?').get(headerPartnerId);
    if (p) {
      return [userId, headerPartnerId];
    }
  }

  const me = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(userId);
  if (me && me.partner_id && me.partner_id !== userId) {
    return [userId, me.partner_id];
  }

  const partners = getUserPartners(userId);
  if (partners.length > 0) {
    return [userId, partners[0].id];
  }

  return [userId];
}

function partnershipFilter(req) {
  const userId = typeof req === 'string' ? req : req?.user?.id;
  if (!userId) return { userIds: [], targetPartnerId: null, sqlClause: '1=0', params: [] };

  const headerPartnerId = (typeof req === 'object' && req?.headers) ? (req.headers['x-partner-id'] || req.query?.partnerId) : null;
  const me = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(userId);
  const targetPartnerId = (headerPartnerId && headerPartnerId !== userId) ? headerPartnerId : (me?.partner_id && me.partner_id !== userId ? me.partner_id : null);

  if (targetPartnerId) {
    const userIds = [userId, targetPartnerId];
    const sqlClause = `(owner_id IN (?, ?) AND (target_user_id IS NULL OR target_user_id IN (?, ?)))`;
    const params = [userId, targetPartnerId, userId, targetPartnerId];
    return { userIds, targetPartnerId, sqlClause, params };
  }

  const userIds = [userId];
  const sqlClause = `(owner_id = ? AND (target_user_id IS NULL OR target_user_id = ?))`;
  const params = [userId, userId];
  return { userIds, targetPartnerId: null, sqlClause, params };
}

function mapReward(r, userIds) {
  const placeholders = userIds.map(() => '?').join(',');
  let redeemed = false;
  if (r.type === 'one_time') {
    const row = db.prepare(`SELECT 1 FROM reward_redemptions WHERE reward_id = ? AND user_id IN (${placeholders}) LIMIT 1`)
      .get(r.id, ...userIds);
    redeemed = !!row;
  }
  return {
    id: r.id,
    ownerId: r.owner_id,
    targetUserId: r.target_user_id,
    title: r.title,
    description: r.description,
    cost: r.cost,
    imageUrl: r.image_url,
    type: r.type || 'recurring',
    redeemed: redeemed,
    order: r.order || 0,
    createdAt: r.created_at
  };
}

app.get('/rewards', auth, (req, res) => {
  const filter = partnershipFilter(req);
  const rewards = db.prepare(`SELECT * FROM rewards WHERE ${filter.sqlClause} ORDER BY "order" ASC, created_at ASC`).all(...filter.params);
  res.json(rewards.map(r => mapReward(r, filter.userIds)));
});

app.post('/rewards', auth, (req, res) => {
  const { title, description, cost = 0, imageUrl, image_url, type, order, target_user_id, targetUserId } = req.body;
  if (!title) return res.status(400).json({ error: 'title required' });
  const id = uuidv4();
  const img = image_url || imageUrl || null;
  const rewardOrder = order ?? 0;
  const rewardType = type || 'recurring';
  const filter = partnershipFilter(req);
  const finalTargetUserId = target_user_id || targetUserId || filter.targetPartnerId || null;

  db.prepare('INSERT INTO rewards (id, owner_id, target_user_id, title, description, cost, image_url, type, "order", created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
    .run(id, req.user.id, finalTargetUserId, title, description || null, cost, img, rewardType, rewardOrder, Date.now());
  const reward = db.prepare('SELECT * FROM rewards WHERE id = ?').get(id);
  const result = mapReward(reward, filter.userIds);
  notifyPartner(req.user.id, { type: 'REWARD_CREATED', reward: result });
  res.status(201).json(result);
});

app.put('/rewards/:id', auth, (req, res) => {
  const r = db.prepare('SELECT * FROM rewards WHERE id = ?').get(req.params.id);
  if (!r) return res.status(404).json({ error: 'Reward not found' });
  const { title, description, cost, imageUrl, image_url, type, order } = req.body;
  const rewardOrder = order !== undefined ? order : r.order;
  const rewardType = type !== undefined ? type : (r.type || 'recurring');
  const img = image_url !== undefined ? image_url : (imageUrl !== undefined ? imageUrl : r.image_url);
  db.prepare('UPDATE rewards SET title=?, description=?, cost=?, image_url=?, type=?, "order"=? WHERE id=?')
    .run(
      title ?? r.title,
      description !== undefined ? description : r.description,
      cost ?? r.cost,
      img,
      rewardType,
      rewardOrder,
      req.params.id
    );
  const updated = db.prepare('SELECT * FROM rewards WHERE id = ?').get(req.params.id);
  const ids = partnerIds(req.user.id);
  const result = mapReward(updated, ids);
  notifyPartner(req.user.id, { type: 'REWARD_UPDATED', reward: result });
  res.json(result);
});

app.delete('/rewards/:id', auth, (req, res) => {
  const r = db.prepare('SELECT * FROM rewards WHERE id = ?').get(req.params.id);
  if (!r) {
    return res.json({ success: true });
  }

  db.prepare('DELETE FROM reward_redemptions WHERE reward_id = ?').run(req.params.id);
  db.prepare('DELETE FROM rewards WHERE id = ?').run(req.params.id);

  notifyPartner(req.user.id, { type: 'REWARD_DELETED', rewardId: req.params.id });
  res.json({ success: true });
});

app.post('/rewards/reorder', auth, (req, res) => {
  const rewardIds = req.body;
  if (!Array.isArray(rewardIds)) return res.status(400).json({ error: 'Array of IDs required' });
  console.log(`Reordering rewards: ${rewardIds.join(', ')}`);

  db.transaction(() => {
    rewardIds.forEach((id, index) => {
      db.prepare('UPDATE rewards SET "order" = ? WHERE id = ?').run(index, id);
    });
  })();

  notifyPartner(req.user.id, { type: 'REWARDS_REORDERED' });
  res.json({ success: true });
});

app.post('/rewards/:id/redeem', auth, (req, res) => {
  const reward = db.prepare('SELECT * FROM rewards WHERE id = ?').get(req.params.id);
  if (!reward) return res.status(404).json({ error: 'Reward not found' });
  const ids = partnerIds(req.user.id);
  const placeholders = ids.map(() => '?').join(',');

  if (reward.type === 'one_time') {
    const done = db.prepare(`SELECT 1 FROM reward_redemptions WHERE reward_id = ? AND user_id IN (${placeholders}) LIMIT 1`)
      .get(req.params.id, ...ids);
    if (done) return res.status(409).json({ error: 'One-time reward already redeemed' });
  }

  const me = db.prepare('SELECT points FROM users WHERE id = ?').get(req.user.id);
  if (me.points < reward.cost) return res.status(400).json({ error: `Need ${reward.cost} points, have ${me.points}` });

  const redeem = db.transaction(() => {
    db.prepare('INSERT INTO reward_redemptions (id, reward_id, user_id, approved, redeemed_at) VALUES (?, ?, ?, 1, ?)')
      .run(uuidv4(), req.params.id, req.user.id, Date.now());
    db.prepare('UPDATE users SET points = points - ? WHERE id = ?').run(reward.cost, req.user.id);
    return db.prepare('SELECT points FROM users WHERE id = ?').get(req.user.id).points;
  });

  const points = redeem();
  notifyPartner(req.user.id, { type: 'REWARD_REDEEMED', rewardId: req.params.id, rewardTitle: reward.title, userId: req.user.id, points });
  res.json({ success: true, points });
});

app.post('/rewards/:id/reset', auth, (req, res) => {
  const reward = db.prepare('SELECT * FROM rewards WHERE id = ?').get(req.params.id);
  if (!reward) return res.status(404).json({ error: 'Reward not found' });

  db.prepare('DELETE FROM reward_redemptions WHERE reward_id = ?').run(req.params.id);
  notifyPartner(req.user.id, { type: 'REWARD_RESET', rewardId: req.params.id });
  res.json({ success: true });
});

// ─────────────────────────────────────────────
// Punishments routes
// ─────────────────────────────────────────────
app.get('/punishments', auth, (req, res) => {
  const filter = partnershipFilter(req);
  const rows = db.prepare(`SELECT * FROM punishments WHERE ${filter.sqlClause} ORDER BY "order" ASC, created_at ASC`).all(...filter.params);
  res.json(rows.map(r => ({
    id: r.id,
    ownerId: r.owner_id,
    targetUserId: r.target_user_id,
    title: r.title,
    description: r.description,
    order: r.order || 0,
    createdAt: r.created_at
  })));
});

app.post('/punishments', auth, (req, res) => {
  const { title, description, order, target_user_id, targetUserId } = req.body;
  if (!title) return res.status(400).json({ error: 'title required' });
  const id = uuidv4();
  const punishmentOrder = order ?? 0;
  const filter = partnershipFilter(req);
  const finalTargetUserId = target_user_id || targetUserId || filter.targetPartnerId || null;

  db.prepare('INSERT INTO punishments (id, owner_id, target_user_id, title, description, "order", created_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
    .run(id, req.user.id, finalTargetUserId, title, description || null, punishmentOrder, Date.now());
  const punishment = db.prepare('SELECT * FROM punishments WHERE id = ?').get(id);
  notifyPartner(req.user.id, { type: 'PUNISHMENT_CREATED', punishment });
  res.status(201).json(punishment);
});

app.put('/punishments/:id', auth, (req, res) => {
  const ids = partnerIds(req.user.id);
  const placeholders = ids.map(() => '?').join(',');
  const p = db.prepare(`SELECT * FROM punishments WHERE id = ? AND owner_id IN (${placeholders})`).get(req.params.id, ...ids);
  if (!p) return res.status(404).json({ error: 'Punishment not found' });
  const { title, description, order } = req.body;
  const punishmentOrder = order !== undefined ? order : p.order;
  db.prepare('UPDATE punishments SET title=?, description=?, "order"=? WHERE id=?')
    .run(title ?? p.title, description !== undefined ? description : p.description, punishmentOrder, req.params.id);
  const updated = db.prepare('SELECT * FROM punishments WHERE id = ?').get(req.params.id);
  notifyPartner(req.user.id, { type: 'PUNISHMENT_UPDATED', punishment: updated });
  res.json(updated);
});

app.delete('/punishments/:id', auth, (req, res) => {
  const ids = partnerIds(req.user.id);
  const placeholders = ids.map(() => '?').join(',');
  const p = db.prepare(`SELECT * FROM punishments WHERE id = ? AND owner_id IN (${placeholders})`).get(req.params.id, ...ids);
  if (!p) return res.status(404).json({ error: 'Punishment not found' });

  db.prepare('DELETE FROM punishment_assignments WHERE punishment_id = ?').run(req.params.id);
  db.prepare('DELETE FROM punishments WHERE id = ?').run(req.params.id);

  notifyPartner(req.user.id, { type: 'PUNISHMENT_DELETED', punishmentId: req.params.id });
  res.json({ success: true });
});

app.post('/punishments/reorder', auth, (req, res) => {
  const punishmentIds = req.body;
  if (!Array.isArray(punishmentIds)) return res.status(400).json({ error: 'Array of IDs required' });
  console.log(`Reordering punishments: ${punishmentIds.join(', ')}`);

  db.transaction(() => {
    punishmentIds.forEach((id, index) => {
      db.prepare('UPDATE punishments SET "order" = ? WHERE id = ?').run(index, id);
    });
  })();

  notifyPartner(req.user.id, { type: 'PUNISHMENTS_REORDERED' });
  res.json({ success: true });
});

app.post('/punishments/:id/assign', auth, (req, res) => {
  const punishment = db.prepare('SELECT * FROM punishments WHERE id = ?').get(req.params.id);
  if (!punishment) return res.status(404).json({ error: 'Punishment not found' });
  const filter = partnershipFilter(req);
  const me = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(req.user.id);
  const targetId = req.body.assignToId || req.body.assign_to_id || filter.targetPartnerId || me?.partner_id;
  if (!targetId) return res.status(400).json({ error: 'No partner to assign to' });
  const { reason } = req.body;
  const id = uuidv4();
  db.prepare(`
    INSERT INTO punishment_assignments (id, punishment_id, assigned_to, assigned_by, reason, completed, assigned_at)
    VALUES (?, ?, ?, ?, ?, 0, ?)
  `).run(id, req.params.id, targetId, req.user.id, reason || null, Date.now());
  notifyPartner(req.user.id, { type: 'PUNISHMENT_ASSIGNED', assignmentId: id, punishment, assignedTo: targetId, reason });
  res.status(201).json({ success: true, assignmentId: id });
});

app.get('/punishments/assigned', auth, (req, res) => {
  const ids = partnerIds(req);
  const placeholders = ids.map(() => '?').join(',');

  const rows = db.prepare(`
    SELECT pa.*, p.title, p.description, p.owner_id
    FROM punishment_assignments pa
    JOIN punishments p ON pa.punishment_id = p.id
    WHERE pa.assigned_to IN (${placeholders}) AND pa.completed = 0
    ORDER BY pa.assigned_at DESC
  `).all(...ids);

  res.json(rows.map(r => ({
    id: r.id,
    punishmentId: r.punishment_id,
    assignedTo: r.assigned_to,
    assignedBy: r.assigned_by,
    reason: r.reason,
    completed: !!r.completed,
    assignedAt: r.assigned_at,
    title: r.title,
    description: r.description,
    ownerId: r.owner_id
  })));
});

app.post('/punishments/assignments/:id/complete', auth, (req, res) => {
  const assignment = db.prepare(
    'SELECT * FROM punishment_assignments WHERE id = ?'
  ).get(req.params.id);
  if (!assignment) return res.status(404).json({ error: 'Assignment not found' });

  const ids = partnerIds(req);
  if (!ids.includes(assignment.assigned_to) && !ids.includes(assignment.assigned_by) && req.user.role !== 'dominant') {
    return res.status(403).json({ error: 'Forbidden' });
  }

  db.prepare('UPDATE punishment_assignments SET completed = 1 WHERE id = ?').run(req.params.id);
  notifyPartner(req.user.id, { type: 'PUNISHMENT_COMPLETED', assignmentId: req.params.id });
  res.json({ success: true });
});

// ─────────────────────────────────────────────
// Points routes
// ─────────────────────────────────────────────
app.post('/points/adjust', auth, (req, res) => {
  const { amount, reason, userId, targetUserId } = req.body;
  if (typeof amount !== 'number') return res.status(400).json({ error: 'amount (number) required' });

  const filter = partnershipFilter(req);
  const me = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(req.user.id);
  const targetId = userId || targetUserId || filter.targetPartnerId || me?.partner_id || req.user.id;

  db.prepare('UPDATE users SET points = MAX(0, points + ?) WHERE id = ?').run(amount, targetId);
  const userRow = db.prepare('SELECT points FROM users WHERE id = ?').get(targetId);
  const points = userRow ? userRow.points : 0;

  notifyPartner(req.user.id, { type: 'POINTS_ADJUSTED', userId: targetId, points, amount, reason });

  const ws = connections.get(targetId);
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: 'POINTS_ADJUSTED', userId: targetId, points, amount, reason }));
  }

  res.json({ success: true, userId: targetId, points });
});

// ─────────────────────────────────────────────
// Admin / Server User Management routes
// ─────────────────────────────────────────────
app.get('/admin/users', auth, (req, res) => {
  try {
    const users = db.prepare('SELECT * FROM users ORDER BY created_at ASC').all();
    res.json(users.map(u => ({
      ...safeUser(u),
      partner: u.partner_id ? safeUser(db.prepare('SELECT * FROM users WHERE id = ?').get(u.partner_id)) : null
    })));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.put('/admin/users/:id', auth, (req, res) => {
  try {
    const targetUser = db.prepare('SELECT * FROM users WHERE id = ?').get(req.params.id);
    if (!targetUser) return res.status(404).json({ error: 'User not found' });

    const { username, role, points, partnerId } = req.body;
    const newUsername = username && username.trim() ? username.trim() : targetUser.username;
    const newRole = role || targetUser.role;
    const newPoints = typeof points === 'number' ? points : targetUser.points;
    const newPartnerId = partnerId !== undefined ? partnerId : targetUser.partner_id;

    db.prepare(`
      UPDATE users SET
        username = ?,
        role = ?,
        points = ?,
        partner_id = ?
      WHERE id = ?
    `).run(newUsername, newRole, newPoints, newPartnerId, req.params.id);

    const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(req.params.id);
    res.json(safeUser(updated));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/admin/users/:id/password', auth, (req, res) => {
  try {
    const { password } = req.body;
    if (!password || !password.trim()) {
      return res.status(400).json({ error: 'New password required' });
    }

    const targetUser = db.prepare('SELECT * FROM users WHERE id = ?').get(req.params.id);
    if (!targetUser) return res.status(404).json({ error: 'User not found' });

    const passwordHash = bcrypt.hashSync(password.trim(), 12);
    db.prepare('UPDATE users SET password_hash = ? WHERE id = ?').run(passwordHash, req.params.id);

    res.json({ success: true, message: 'Password updated successfully' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.delete('/admin/users/:id', auth, (req, res) => {
  try {
    const targetUser = db.prepare('SELECT * FROM users WHERE id = ?').get(req.params.id);
    if (!targetUser) return res.status(404).json({ error: 'User not found' });

    db.transaction(() => {
      // Unpair partner if linked
      db.prepare('UPDATE users SET partner_id = NULL WHERE partner_id = ?').run(req.params.id);
      // Delete user
      db.prepare('DELETE FROM users WHERE id = ?').run(req.params.id);
    })();

    res.json({ success: true, message: 'User deleted successfully' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ─── History ─────────────────────────────────────────────────────────────
app.get('/habits/history', auth, (req, res) => {
  const filter = partnershipFilter(req);

  const rows = db.prepare(`
    SELECT DISTINCT hc.habit_id, hc.completed_at
    FROM habit_completions hc
    JOIN habits h ON hc.habit_id = h.id
    WHERE h.id IN (SELECT id FROM habits WHERE ${filter.sqlClause})
    ORDER BY hc.completed_at DESC
  `).all(...filter.params);

  const history = [];
  const seenKeys = new Set();
  rows.forEach(r => {
    const dateStr = formatCompletionDate(r.completed_at);
    if (dateStr) {
      const key = `${r.habit_id}_${dateStr}`;
      if (!seenKeys.has(key)) {
        seenKeys.add(key);
        history.push({
          habitId: r.habit_id,
          date: dateStr
        });
      }
    }
  });

  res.json(history);
});

// ─────────────────────────────────────────────
// Stats routes
// ─────────────────────────────────────────────
app.get('/stats', auth, (req, res) => {
  try {
    const thirtyDaysAgo = Date.now() - 30 * 86400000;

    const me = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
    const filter = partnershipFilter(req);

    let targetUserId = req.user.id;
    if (me && me.role === 'dominant') {
      if (filter.targetPartnerId) {
        targetUserId = filter.targetPartnerId;
      } else if (me.partner_id) {
        targetUserId = me.partner_id;
      }
    }

    const habits = db.prepare(`SELECT * FROM habits WHERE ${filter.sqlClause}`).all(...filter.params);

    const completions = db.prepare(`
      SELECT DISTINCT hc.habit_id, hc.completed_at, h.title, h.frequency, h.is_positive
      FROM habit_completions hc
      JOIN habits h ON hc.habit_id = h.id
      WHERE h.id IN (SELECT id FROM habits WHERE ${filter.sqlClause})
      ORDER BY hc.completed_at DESC
    `).all(...filter.params);

    const targetUser = db.prepare('SELECT points FROM users WHERE id = ?').get(targetUserId);
    const points = targetUser ? targetUser.points : 0;

    // Filter completions to positive habits only for statistics
    const positiveCompletions = completions.filter(c => c.is_positive === 1);

    // Streak: count consecutive days with at least one positive daily habit completed
    const dailyPositiveHabits = habits.filter(h => h.frequency === 'daily' && h.is_positive === 1);
    let streak = 0;
    if (dailyPositiveHabits.length > 0) {
      const today = new Date(); today.setHours(0, 0, 0, 0);
      for (let i = 0; i <= 365; i++) {
        const dayStart = today.getTime() - i * 86400000;
        const dayEnd = dayStart + 86400000;
        const hasCompletion = positiveCompletions.some(c => {
          let ms = Number(c.completed_at);
          if (isNaN(ms)) ms = Date.parse(c.completed_at);
          if (ms < 1e11) ms *= 1000;
          return ms >= dayStart && ms < dayEnd && c.frequency === 'daily';
        });
        if (!hasCompletion) break;
        streak++;
      }
    }

    // Completions by day (last 30 days) for chart
    const byDay = {};
    for (let i = 29; i >= 0; i--) {
      const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - i);
      byDay[d.toISOString().split('T')[0]] = 0;
    }
    positiveCompletions.forEach(c => {
      const dateStr = formatCompletionDate(c.completed_at);
      if (dateStr && byDay[dateStr] !== undefined) byDay[dateStr]++;
    });

    const completionsLast30 = positiveCompletions.filter(c => {
      let ms = Number(c.completed_at);
      if (isNaN(ms)) ms = Date.parse(c.completed_at);
      if (ms < 1e11) ms *= 1000;
      return ms >= thirtyDaysAgo;
    });

    res.json({
      totalHabits: habits.length,
      totalCompletionsLast30Days: completionsLast30.length,
      currentPoints: points,
      streak,
      completionsByDay: Object.entries(byDay).map(([date, count]) => ({ date, count })),
      recentCompletions: positiveCompletions.slice(0, 20).map(c => ({
        habitId: c.habit_id,
        completedAt: c.completed_at,
        title: c.title,
        frequency: c.frequency
      })),
    });
  } catch (err) {
    console.error('Error in GET /stats:', err);
    res.status(500).json({ error: 'Failed to fetch stats' });
  }
});

// ─────────────────────────────────────────────
// File uploads
// ─────────────────────────────────────────────

// Serve uploaded files as static assets
// Subfolder route: /uploads/:context/:userId/:filename
app.get('/uploads/:context/:userId/:filename', (req, res) => {
  const filePath = path.join(UPLOADS_DIR, req.params.context, req.params.userId, req.params.filename);
  if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'File not found' });
  const resolved = path.resolve(filePath);
  if (!resolved.startsWith(path.resolve(UPLOADS_DIR))) return res.status(403).json({ error: 'Forbidden' });
  res.sendFile(resolved);
});

// Legacy fallback route for flat uploads: /uploads/:context/:filename
app.get('/uploads/:context/:filename', (req, res) => {
  const filePath = path.join(UPLOADS_DIR, req.params.context, req.params.filename);
  if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'File not found' });
  const resolved = path.resolve(filePath);
  if (!resolved.startsWith(path.resolve(UPLOADS_DIR))) return res.status(403).json({ error: 'Forbidden' });
  res.sendFile(resolved);
});

// Upload an image for a given context (avatar, reward, punishment, habit)
// POST /uploads/:context  — multipart/form-data with field name "file"
// Returns: { url: "/uploads/<context>/<userId>/<filename>" }
app.post('/uploads/:context', auth, (req, res, next) => {
  const allowed = new Set(['avatars', 'rewards', 'punishments', 'habits']);
  if (!allowed.has(req.params.context)) return res.status(400).json({ error: 'Invalid upload context' });
  next();
}, upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file uploaded' });
  const url = `/uploads/${req.params.context}/${req.user.id}/${req.file.filename}`;

  // If this is an avatar upload, automatically link it to the user profile
  if (req.params.context === 'avatars') {
    db.prepare('UPDATE users SET avatar_url = ? WHERE id = ?').run(url, req.user.id);
    // Notify partner that our profile picture changed
    notifyPartner(req.user.id, {
      type: 'AVATAR_UPDATED',
      userId: req.user.id,
      avatarUrl: url
    });
  }

  res.json({ url, filename: req.file.filename, size: req.file.size, mimetype: req.file.mimetype });
});

// Delete an upload
app.delete('/uploads/:context/:userId/:filename', auth, (req, res) => {
  const filePath = path.join(UPLOADS_DIR, req.params.context, req.params.userId, req.params.filename);
  const resolved = path.resolve(filePath);
  if (!resolved.startsWith(path.resolve(UPLOADS_DIR))) return res.status(403).json({ error: 'Forbidden' });
  if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'File not found' });
  fs.unlinkSync(filePath);
  res.json({ success: true });
});

app.delete('/uploads/:context/:filename', auth, (req, res) => {
  const filePath = path.join(UPLOADS_DIR, req.params.context, req.params.filename);
  const resolved = path.resolve(filePath);
  if (!resolved.startsWith(path.resolve(UPLOADS_DIR))) return res.status(403).json({ error: 'Forbidden' });
  if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'File not found' });
  fs.unlinkSync(filePath);
  res.json({ success: true });
});

// Multer error handler
app.use((err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE')
      return res.status(413).json({ error: `File too large. Max ${MAX_FILE_SIZE / 1024 / 1024}MB` });
    return res.status(400).json({ error: err.message });
  }
  if (err?.message?.startsWith('File type not allowed')) return res.status(415).json({ error: err.message });
  next(err);
});

// ─────────────────────────────────────────────
// Health & Debug check
// ─────────────────────────────────────────────
app.get('/health', (_, res) => {
  try {
    const habitsSchema = db.prepare("PRAGMA table_info(habits)").all();
    const rewardsSchema = db.prepare("PRAGMA table_info(rewards)").all();
    res.json({
      status: 'ok',
      time: new Date().toISOString(),
      schema: { habits: habitsSchema, rewards: rewardsSchema }
    });
  } catch (e) {
    res.json({ status: 'error', error: e.message });
  }
});

app.get('/debug/db', (_, res) => {
  try {
    const userCount = db.prepare("SELECT count(*) as c FROM users").get()?.c || 0;
    const habitCount = db.prepare("SELECT count(*) as c FROM habits").get()?.c || 0;
    const completionCount = db.prepare("SELECT count(*) as c FROM habit_completions").get()?.c || 0;
    const submissionCount = db.prepare("SELECT count(*) as c FROM habit_submissions").get()?.c || 0;
    const habits = db.prepare("SELECT id, title, owner_id, frequency, created_at FROM habits").all();
    const users = db.prepare("SELECT id, username, role, points, pair_code, partner_id FROM users").all();

    res.json({
      status: 'ok',
      counts: {
        users: userCount,
        habits: habitCount,
        completions: completionCount,
        submissions: submissionCount
      },
      users,
      habits
    });
  } catch (e) {
    res.status(500).json({ status: 'error', error: e.message });
  }
});

app.get('/debug/recover', (_, res) => {
  try {
    const { runRecovery } = require('./recover.js');
    const result = runRecovery();
    autoHealDatabase(db);
    res.json(result);
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/recover', (_, res) => {
  try {
    const { runRecovery } = require('./recover.js');
    const result = runRecovery();
    autoHealDatabase(db);
    res.json(result);
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─────────────────────────────────────────────
// Backup routes
// ─────────────────────────────────────────────
app.get('/backups', (req, res) => {
  res.json({ backups: listDatabaseBackups() });
});

app.post('/backups/create', async (req, res) => {
  const result = await createDatabaseBackup('manual');
  if (result.success) {
    res.json(result);
  } else {
    res.status(500).json(result);
  }
});

app.post('/backups/restore', async (req, res) => {
  const { filename } = req.body;
  if (!filename) return res.status(400).json({ error: 'filename required' });

  const safeFilename = path.basename(filename);
  const backupPath = path.join(BACKUPS_DIR, safeFilename);
  if (!fs.existsSync(backupPath)) return res.status(404).json({ error: 'Backup file not found' });

  try {
    // 1. Create safety backup of current state
    await createDatabaseBackup('pre-restore-safety');

    // 2. Perform online restore from selected backup file
    const backupDb = new Database(backupPath);
    await backupDb.backup(DB_PATH);
    backupDb.close();

    // 3. Re-run auto-heal on current db
    autoHealDatabase(db);

    console.log(`✅ Successfully restored database from ${safeFilename}`);
    res.json({ success: true, message: `Successfully restored database from ${safeFilename}` });
  } catch (err) {
    console.error('Error restoring backup:', err);
    res.status(500).json({ error: `Restore failed: ${err.message}` });
  }
});

// ─────────────────────────────────────────────
// WebSocket
// ─────────────────────────────────────────────
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

/** userId → WebSocket */
const connections = new Map();

wss.on('connection', (ws) => {
  let userId = null;

  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(raw); } catch { return; }

    if (msg.type === 'AUTH') {
      try {
        const decoded = jwt.verify(msg.token, JWT_SECRET);
        userId = decoded.id;
        connections.set(userId, ws);
        ws.send(JSON.stringify({ type: 'AUTH_SUCCESS', userId }));
      } catch {
        ws.send(JSON.stringify({ type: 'AUTH_ERROR', error: 'Invalid token' }));
      }
    }

    if (msg.type === 'PING') {
      ws.send(JSON.stringify({ type: 'PONG' }));
    }
  });

  ws.on('close', () => { if (userId) connections.delete(userId); });
  ws.on('error', () => { if (userId) connections.delete(userId); });
});

function send(userId, event) {
  const ws = connections.get(userId);
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(event));
  }
}

function notifyPartner(fromUserId, event) {
  const row = db.prepare('SELECT partner_id FROM users WHERE id = ?').get(fromUserId);
  if (row?.partner_id) send(row.partner_id, event);
}

function broadcastToUsers(userIds, event) {
  userIds.forEach(id => send(id, event));
}

// ─────────────────────────────────────────────
// Start
// ─────────────────────────────────────────────
server.listen(PORT, '0.0.0.0', () => {
  console.log(`✅ Discipline local server running on port ${PORT}`);
  console.log(`   Database: ${DB_PATH}`);
  console.log(`   Connect your Android app to: http://<pi-ip>:${PORT}`);

  scheduleDailyBackup();
});
