# Bubie

**A little CRT robot that works for you: it chops trees, digs sand, quarries stone, farms and mines, then puts everything in your chest.**

*🇧🇷 Versão em português mais abaixo.*

Bubie is the mascot of bubie.gg, a cream CRT monitor in a black hoodie, as a helper for Minecraft 1.21.1 (NeoForge). Craft one, click the ground to switch it on, and give it a job.

## 🤖 What it does
- **Follow:** walks after you and picks up items on the ground. **Stay:** sits and waits. **Work:** does its job in an area.
- **Lumberjack:** fells whole trees and replants the same sapling. Leaves your log cabins alone.
- **Digger:** sand, gravel, dirt and clay, layer by layer (filters in the menu).
- **Quarry:** digs out the whole area and leaves a staircase on the walls. Plugs water and lava with cobblestone first.
- **Farmer:** harvests and replants crops; sugar cane, cactus, bamboo, pumpkins, melons, cocoa, nether wart, sweet berries.
- **Miner:** exposed ore veins, or branch tunnels with torches. Uses the `c:ores` tag, so modded ores count.
- **Collector:** gathers every item lying in the area.

## 📋 How to use it
1. Craft the **Bubie Clipboard**, click two blocks to mark the area, Shift + click a chest.
2. Click your Bubie with the clipboard. It starts working and brings everything back to the chest.
3. Give it a tool (it uses your axe, shovel or pickaxe, and stops before it breaks) and some coal.

## ⚡ Energy, home and chips
- Coal gives 10 minutes of work. The **Bubie Station** charges it with FE or fuel, stores what it brings back, and is where it rests at night.
- Four chip slots: Speed, Storage, Range, Magnet, Smelting and Efficiency.
- At 0 health it **shuts down** into its item, keeping everything. It runs from monsters and never dies.
- Breaks blocks as its owner, so claim protection (FTB Chunks) applies. A balloon over its head says what's wrong when it stops.

## 🎨 Edit my Bubie
Casing, hoodie and screen-light colours, 18 eyes and 10 mouths from the bubie.gg My Bubie editor, with a turning preview. Everyone on the server sees your look.

## 🔌 Works with
- **Storage:** unloads into any block with an inventory: Sophisticated Storage, Functional Storage, Create vaults, Mekanism bins, chests (AE2: put a Storage Bus on its chest).
- **Power:** the station takes FE from any cable (Mekanism, Powah, Flux Networks, Ender IO, Pipez...), and the Bubie goes back to charge on its own, so it never runs out. Pipes can feed coal to the station.
- **Claims and teams:** breaks blocks as its owner, so FTB Chunks protection applies; FTB Teams teammates can give it orders.
- **Crops and ores:** Mystical Agriculture, Farmer's Delight and other standard crops; any ore in `c:ores` (All the Ores included); any modded tool.
- **Safe from automation:** mob capturers can't take it, and machines that attack as fake players don't hurt it.
- **Jade** shows its job, status and battery; **JEI/EMI** show the recipes.

Settings in `serverconfig/bubie-server.toml`.

---

## 🇧🇷 Português
O Bubie, mascote do bubie.gg, vira um ajudante: corta árvores inteiras e replanta, escava areia e terra, abre pedreiras com escada,
colhe e replanta plantações, minera veios ou túneis e junta itens do chão, guardando tudo no seu baú.
Marque a área com a **Prancheta do Bubie**, clique no Bubie com ela e dê uma ferramenta e um pouco de carvão.
A **Estação do Bubie** carrega com FE ou combustível e é a casa dele. Se a vida acaba, ele desliga e vira o item, sem perder nada.

Made for the Bubie Pack. MIT license.
