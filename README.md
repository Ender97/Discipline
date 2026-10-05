# Discipline by Ender Accord (Ender97)

A flexible, private, self-hosted relationship habit tracker app for Android. Your data lives on your network. No  subscriptions or third parties.

\---\

## What's the point of this?

If you know, you know. 

But if you don't, Discipline is a privacy-forward habit tracking app designed for relationships with a power dynamic. The general idea is to set up habits for an s-type partner to complete and earn points to spend on rewards. It's built for both stock Android and GrapheneOS. Here's what it does:

**Habit/Task Tracking**
- Habit and task tracking with optional photo proof
- Habits = repeatable actions daily or weekly, can be assigned as to-do or to-avoid. 
- Tasks = one-time actions with optional due dates, which are checked off once completed
- Point rewards for completion, automated penalty tracking for failure to complete
- Dominant can view and edit the submissive's habits historical data
- Habits can be paused or resumed at any time
- Notifications that are not dependent on external services such as google play (using a persistent notification that can be enabled or disabled for battery optimization) 

**Rewards & Puishments**
- Reward points are accrued from successful habits and tasks, or can be manually adjusted by a D-type partner
- Points can be redeemed by an s-type partner for rewards 
- Rewards can be recurring (redeemed infinitely) or one-time (checked off as completed)
- Punishments can be linked to a habit to track automatically or assigned at any time

**Monthly check-ins**
- At the start of each month, both partners complete a short reflection: written notes, goal progress, and a satisfaction rating
- The app sends a notification reminder on days 1–7 of each month

**Stats**
- Calendar feature with all habits/tasks viewable at a glance with submitted photos 
- D-type partner can easily adjust historical data from calendar 
- Current streak, total points, completions over the last 30 days
- Bar chart of daily activity

**Partners & Pairing**
Each user account has a six-character pair code shown on the Profile screen. Share your code with a partner, and they'll enter it in the app to link the accounts. You can pair with multiple partners as well. Switch between partners easily from your Profile screen.

**Solo / offline mode**
- The app can be used offline without any partners, with all data stored locally on the phone instead of a server
- This was developed mainly so functionality could be tested offline, but also enables habit tracking for solo users!

\---\

## About the Server

**Requirements:** Node.js 20+ running on some sort of Linux device on your LAN (Raspberry Pi recommended)

**Ultra mega quick start:**
Create a directory called `discipline-server` at /home/ and drop in the server files. Open a new terminal window and run the following: 
```
cd discipline-server
npm install
node setup.js
```
Feel free to mash the Enter button a few times to accept all the default settings, then: 
```
pm2 start index.js --name discipline-api
pm2 save && pm2 startup
```

See [`server/README.md`](server/README.md) for a more verbose setup guide with additional notes on pm2 startup scripts, finding your Pi's IP, and optional remote access setup via Tailscale.

**What this server manages:**
- User accounts and partner pairing (including in-app controls for managing users and resetting passwords)
- All habit/task, reward, punishment, and points data in a SQLite database
- Photo proof uploads (10MB max by default, you can alter this in setup)
- Automatic daily database backups with a rolling 30-day retention (you'll thank me later)

**Remote access**
It's not strictly necessary, but I personally recommend using [Tailscale](https://tailscale.com) to access your server away from home. Use your server's Tailscale IP (`100.x.x.x`) as the server address instead of the local one. If the app has trouble connecting, it will show a dedicated "server unreachable" screen with a shortcut to open Tailscale. This setup has proven very doable even for users who are less technically inclined.

Tailscale is built on Wireguard. It's end to end encrypted and secure, meaning your data is untouchable and stays 100% private between only your devices. However, it's worth noting that the Tailscale software is not open source. If you're a nerd, there are self-hosted options for accessing away from home. You may already be doing that. Other solutions for accessing your home network remotely (such as Headscale, plain wireguard, port forwarding thru VPN, etc.) are outside the scope of this guide. I encourage you to find a solution that works for you and your partner(s). 

**Endpoints and notes for nerds:**
- `GET /health` — server status and schema info
- `GET /backups` — this lists backups; `POST /backups/create` forces one manually
- `GET /admin/users` — list all accounts
The server exposes a REST API and a WebSocket endpoint. Changes sync in real time via WebSocket. Habit completions, point adjustments, punishment assignments, and photo submissions all push instantly to the other device. (But there's a refresh button if that doesn't work.)

\---\

## Privacy and the like

- All server data is stored on your own hardware. Nobody else will ever see it unless you share it. 
- No analytics, no telemetry, no external services needed. I'll never know or care what you get up to. Stay safe, have fun, YKINMKBYKIOK, all that good stuff. 
- Please note that this server is designed for use on a private network or exclusively behind a VPN — several endpoints have no authentication by design (health, debug, backups) and should not be exposed to the public internet. In short, always use protection. 

## This is where I would put my FAQ... if I had one

Q: "Are you related to the Obedience app?"
A: To quote Vizzini the Sicilian, 'not remotely!' This project has no ties to Obedience. I don't know anyone on that team, this app does not use any of their code, and there was no reverse engineering. 

Q: "But does your app do all the same stuff as Obedience?" 
A: Not exactly, but it's designed for the same use case. For full transparency, I was a user and paid subscriber of Obedience for some time, and I respect the work that they've done to make dynamics modern and convenient. I became frustrated with their app and wanted something private, self-hosted, more flexible, and free to use. I'm sharing this personal project only because I hope it will benefit the community. I don't consider it a commercial competitor to Obedience, in the same way that I don't consider Audacity to be a commmercial competitior to Ableton. 

Q: "Did you use AI on this project?"
A: Yes, I used Claude Sonnet 4.6 and Gemini 3 Flash Preview to help me code this app. But the real slop is probably the stuff I did myself. My coding skills are middling at best (I learned via the method of 'search Stack Exchange and cry.') Importantly, no creativity was outsourced. The lovely app icon was drawn by my partner, and all other visual assets are basic style elements built into Android.   

Q: "Will this cost me any money?" 
A: No. This is a FOSS project with no goal of turning a profit. If you appreciate this and would like to support me, you can consider donating here, but no pressure. (https://ko-fi.com/enderaccord)

Q: "Can you make a version of this for iPhone?"
A: Realistically speaking, no. Kotlin is hard enough for me as is. I'm working off a 10 year old laptop and I don't even own an iPhone to use for development. It's not gonna happen. If you want to take a crack at it yourself, this is open source for a reason.

Q: "Please?"
A: All right, here's the deal. In the unlikely event that I achieve a target donation goal of $400 to cover the cost of a refurbished iPhone (as well as my time and tools), I'll suck it up and learn how to do this in Swift or Flutter. Same donation link: (https://ko-fi.com/enderaccord)

However, even if I somehow manage that donation goal, I'm probably not going to be able to shell out $100 yearly out of my own pocket to put it on the app store. It's either going to be TestFlight or sideloading. I won't monetize this, I refuse to consider running ads, and I highly doubt I'd be able to get enough in reliable yearly donations to cover it.

\---\

## License

Copyright (c) 2026 Ender Accord

(MIT)
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Contributing

Issues and pull requests are always welcome. This has been tested mainly on various generations of Pixel phones running stock android or GrapheneOS, so let me know about bugs on other devices. A few probably-relevant things to keep in mind if you want to contribute:

- `setup.js` will make a .env file. Don't commit your .env file
- The `.gitignore` excludes `server/.env`, `server/*.db`, `server/uploads/`, `server/backups/`, and all Android build artifacts
- The `~/discipline-server/` folder is self-contained and has no dependency on the Android project
