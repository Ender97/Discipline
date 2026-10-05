# Discipline by Ender Accord (Ender97)

These are basic instructions to set up the Discipline backend, which is a node.js server. These instructions are specifically made for running on a Raspberry Pi, although the steps may be applicable for any debian based Linux install. You can run a node.js server on pretty much any OS with some research

If you're having issues with the app, check the top of the "Habits" screen for a red text box. This is where network errors will be printed to help with troubleshooting, and they will stay put until you manually clear them or close the app. 

\---\

## First-time setup!

This guide assumes you have created a directory called discipline-server in your home folder and placed the server files inside. 

### 1. Dependencies if you don't have them already

To install dependencies, open a new terminal and run this:
```
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install nodejs
cd ~/discipline-server
npm install
```

### 2. Run the setup tool

To set up the server for the first time, run this from your server directory:
```
node setup.js
```

The first-time setup tool will ask for your preferred port (default is 3000) and will let you set custom paths for where the server will store data. If you're not sure, just hit Enter through all the prompts to use the default settings. This will write a .env file. 

### 3. Install pm2 and start the server

Run this: 
```
sudo npm install -g pm2
pm2 start index.js --name discipline-api
```

This will get your server online. You can name it something else by altering the command.  

### 4. Enable auto-start on system boot

To make sure your server always starts running at power on, you need to save it to the pm2 list and then set pm2 to run at startup.

Run this:
```
pm2 save
pm2 startup
```

The second command is instructional. It will print out a specific command that you can copy and paste right back into the terminal. It should look something like this:
sudo env PATH=$PATH:/usr/bin pm2 startup systemd -u pi --hp /home/pi

## 5. Get your local IP address to put into the app

Run the following command: 
```
hostname -I 
```

The -I modifier is case sensitive. The first URL (usually starting with 192.168) is the IP address of your server. You may want to assign a static IP to the device at the router level to make sure it doesn't change. 

If you are using the default port (3000), you only need to include the local IP of the server and it will default to that port. If not, be sure to format your server URL with the port you're using (e.g. 192.168.1.50:3001).
Now you can access your server and use the app whenever you're connected to your home network.

## Note: some useful pm2 commands

`pm2 logs discipline-api`
(This will show the last 15 lines of output from the server's logs, press ctrl+c to exit)

`pm2 restart discipline-api`
(This will restart the server, you'll need to do this if you make any changes to index.js)

`pm2 stop discipline-api`
(This stops the server, but you'll need to remove it from the saved pm2 list with `pm2 save` if you don't want it to run again at startup)

`pm2 status`
(This will show all your running servers)

\---\

## Note 2: my recommendation for remote access

If you want to access your server away from home, I personally recommend using Tailscale (https://tailscale.com). This will give your Pi a static IP address (100.x.x.x) that you can access anywhere, and you can have up to 6 users added to your tailnet for free. Use the IP address from Tailscale in the app's server URL box instead of the local IP address. All Tailscale traffic is end to end encrypted, and the actual data is stored only on your network. I've even implemented a button to open the Tailscale app when a server can't be reached. 

To install Tailscale on your Pi, run this:
```
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

Important: please note that Tailscale is simply a recommended setup, not a requirement. If you choose to use it, your actual data is still untouchable and stays private between only your devices. However, it's worth noting that the Tailscale software is not open source. A reliable connection will be dependent on a third party service. Personally, I needed everything to "just work" with easy setup for less technical users, and for my purposes the downsides of using Tailscale are basically nonexistent. 

If you're a nerd, there are self-hosted FOSS options for accessing while away from home. But if you're that guy, I bet you're already two steps ahead of this readme anyway. Other solutions for accessing a home network remotely (e.g. Headscale, a different implementation of wireguard, port forwarding thru VPN, etc.) are outside the scope of this guide. I encourage you to find the best solution for you and your partner(s). 

## License

Copyright (c) 2026 Ender Accord

(MIT)
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Contributing

Issues and pull requests are always welcome. This server has only been tested on a Rasbperry Pi 4. A few probably-relevant things to keep in mind if you want to contribute:

- `setup.js` will make a .env file. Don't commit your .env file
- The `.gitignore` excludes `server/.env`, `server/*.db`, `server/uploads/`, `server/backups/`, and all Android build artifacts
- The `~/discipline-server/` folder is self-contained and has no dependency on the Android project
