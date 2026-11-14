# 🐉 Dungeon Court Revive: Legends of the Dragon Realm (v18.0)

A full-scale, 150-level grimdark multiplayer browser RPG—inspired by *Dragon Court*, *Darkest Dungeon*, and *Dark Souls*—running 100% natively inside a single **Hubitat Elevation** Custom App via OAuth endpoints, procedural SVG vector graphics, and a Web Audio API synthesizer.

---

## 📋 Prerequisites & Hardware Notes

* **Supported Hubs:** Hubitat Elevation **C-5, C-7, C-8, or C-8 Pro** (Platform version `2.3.5.x` or newer recommended).
* **Dedicated Spare Hub vs. Main Production Hub:**
  * **Spare Hub (Recommended for Tinkering):** An old C-5 or C-7 makes an awesome dedicated RPG server for your household or friends via Hubitat Cloud links.
  * **Main Production Hub (Safe by Design):** `v18.0` uses **zero** background `runIn()` or `schedule()` cron jobs, compresses item state by **45%**, caches all 15 JavaScript engine chunks in your browser's `localStorage`, and uses **Delta-Only Save Payloads** (80% smaller HTTP POSTs) so it will not bog down your Zigbee/Z-Wave radios or automations.

---

## 🛠️ Step-by-Step Installation Guide

### Step 1: Add the Groovy App Code
1. Open your Hubitat Elevation web interface and navigate to **Developer Tools** ➔ **Apps Code** (or **Apps Code** in the left sidebar).
2. Click **+ New App** in the top-right corner.
3. **Option A — One-Click URL Import (Recommended):**
   * Click the **Import** button at the top of the editor.
   * Paste the Raw GitHub URL:
     ```text
     [https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/DungeonCourtRevive.groovy](https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/DungeonCourtRevive.groovy)
     ```
   * Click **Import** and confirm.
4. **Option B — Manual Copy/Paste:**
   * Open `DungeonCourtRevive.groovy` from this repository, copy the entire file (Parts 1 through 7 combined in order), and paste it into the Hubitat editor.
5. Click **Save** in the top-right corner and wait a few seconds for the Groovy compiler to finish.

---

### Step 2: Enable OAuth (Required)
> ⚠️ **CRITICAL:** The game's `/play` web interface and 15 modular `/js/*` endpoints will not load unless OAuth is enabled.

1. While still inside the **Apps Code** editor for `Dungeon Court Revive (OAuth RPG)`, click the **OAuth** button in the top-right corner.
2. Click **Enable OAuth in App**.
3. Leave the default settings as-is and click **Update**.
4. Click **Save** one final time on the main code editor screen.

---

### Step 3: Install & Configure the User App
1. In your Hubitat sidebar, go to **Apps** ➔ **+ Add User App** (top-right corner).
2. Select **Dungeon Court Revive (OAuth RPG)** from the list.
3. Configure your server preferences under **⚙️ Game Server & Performance Settings**:
   * **Active Hero Slot Cap:** Default is **3 Hero Slots** (recommended for maximum state compression and instant cloud syncs) or **6 Hero Slots** (extended roster).
   * **Enable 'The Unmaking' Server Wipe:** Default is **OFF (Safe Mode)** so the 168-hour random world event never wipes your characters. Turn **ON** only if you want a true hardcore seasonal server wipe.
   * **Enable Debug Logging:** Keep **OFF** for zero hub I/O bloat.
   * **Base Max Quests Capacity:** Default is `30` stamina per hero.
   * **Minutes per +5 Quest & Work Tick:** Default is `45` minutes.
   * **XP & Gold Rate Multiplier:** Choose `1x` (Hardcore Standard), `2x` (Heroic Pace), or `3x` (Fast Track).
4. Click **Done** at the bottom of the page to initialize the game storage and spawn your first World Raid Boss.

---

### Step 4: Launch the Game & Save to Your Phone
1. Re-open **Dungeon Court Revive (OAuth RPG)** from your Hubitat **Apps** list.
2. At the top of the dashboard, you will see two launch buttons:
   * **⚔️ Enter Realm (Local LAN):** Fastest response time when connected to your home Wi-Fi.
   * **🌑 Enter Realm (Remote Cloud):** Play from anywhere over cellular data or share with a friend/family member to play in their own character slot.
3. **Install as a Full-Screen Mobile Web App:**
   * **iOS (Safari):** Open the Cloud or LAN link ➔ tap the **Share** icon ➔ select **Add to Home Screen**.
   * **Android (Chrome):** Open the Cloud or LAN link ➔ tap the **⋮ (Menu)** icon ➔ select **Add to Home screen** / **Install app**.

---

## 🔄 Updating the Code & Clearing Client Cache

Because the game caches its 15 JavaScript chunks (`dcr_v180_*`) inside your browser's `localStorage` to save Hubitat bandwidth, you must refresh the client cache whenever you update the `.groovy` code on your hub:

1. Paste or re-import the updated `.groovy` file in **Apps Code** and click **Save**.
2. Open the game in your browser and tap the **🔄 (Reload/Clear Cache)** button on the far right of the top navigation bar (or append `&nocache=1` to the end of your play URL).
3. The bootloader will pull the fresh scripts from your hub and re-cache them automatically.

---

## 🛡️ Admin Controls & Recovery Tools

Inside the Hubitat App settings page, the Realm Administrator can manage the server at any time without touching code:

* **⚰️ 48-Hour Fallen Crypt Restoration:** If a hero dies with `0` Valkyrie Seals and fails their Soul-Urn Rescue Delve, they appear in the Admin Crypt section for 48 hours. Click **⚡ Restore [Hero Name]** to revive them directly back into their slot.
* **⚡ Admin Reset Button:** Instantly refills all characters' Quest Stamina, HP/MP, daily Sanctuary heals, daily Zone Boss/Overlord caps, and clears Wanted Bounties.
* **🌍 World Boss & Curse Controls:** Manually roll a new World Raid Boss, force-spawn the 1% **Vortex-Omega: The Galactic Destroyer**, clear an active 7-Day Realm Cataclysm Curse, or toggle **The Unmaking** event on/off.
* **💾 JSON Lineage Backups:** Inside the game, open **⚜️ Hero ➔ 💾 JSON** at any time to copy a portable JSON backup string of your character to your clipboard.
