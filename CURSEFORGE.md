# Bubie

**A little CRT robot that works for you: it chops trees, digs sand, quarries stone, farms and mines, then puts everything in your chest.**

*🇧🇷 Versão em português mais abaixo.*

![Seven Bubies, each dressed up in the editor](https://media.forgecdn.net/attachments/2241/432/01-lineup-png.png)

Bubie is the mascot of bubie.gg, a cream CRT monitor in a black hoodie, as a helper for Minecraft 1.21.1 (NeoForge). Craft one, click the ground to switch it on, and give it a job.

![The default Bubie up close](https://media.forgecdn.net/attachments/2241/433/02-portrait-png.png)

## 🤖 What it does
- **Follow:** walks after you and picks up items on the ground. **Stay:** sits and waits. **Work:** does its job in an area.
- **Lumberjack:** fells whole trees and replants the same sapling. Leaves your log cabins alone.
- **Digger:** sand, gravel, dirt and clay, layer by layer (filters in the menu).
- **Quarry:** digs out the whole area and leaves a staircase on the walls. Plugs water and lava with cobblestone first.
- **Farmer:** harvests and replants crops; sugar cane, cactus, bamboo, pumpkins, melons, cocoa, nether wart, sweet berries.
- **Miner:** exposed ore veins, or branch tunnels with torches. Uses the `c:ores` tag, so modded ores count.
- **Collector:** gathers every item lying in the area.
- **Builder** (with Base Blueprint): hand it a configured blueprint where the base should go and it builds it block by block, taking the blocks from its chest. Out of blocks, it waits and says which block it needs; another Bubie (a Lumberjack or a Quarry) can keep that chest filled while it builds. With Base Blueprint 1.3.0+, **Build with Bubie** in the blueprint menu sends your nearest Bubie to build where you are looking.
- **Courier:** empties the chests, hoppers and furnace outputs in its area into its own chest. Feeding a Builder, it carries only what the blueprint still needs.

Several Bubies in one area split the work: each one claims the block (or, for lumberjacks, the whole tree) it is on.

![A quarry with its staircase](https://media.forgecdn.net/attachments/2241/438/07-quarry-png.png)

## 📋 How to use it
1. Craft the **Bubie Clipboard**, click two blocks to mark the area, Shift + click a chest.
2. Click your Bubie with the clipboard. It starts working and brings everything back to the chest.
3. Give it a tool (it uses your axe, shovel or pickaxe, and stops before it breaks) and some coal.

| | |
|---|---|
| ![The Job tab](https://media.forgecdn.net/attachments/2241/436/05-menu_job-png.png) | ![The Status tab](https://media.forgecdn.net/attachments/2241/437/06-menu_status-png.png) |

## ⚡ Energy, home and chips
- Coal gives 10 minutes of work. The **Bubie Station** charges it with FE or fuel, stores what it brings back, and is where it rests at night.
- Four chip slots: Speed, Storage, Range, Magnet, Smelting and Efficiency.
- At 0 health it **shuts down** into its item, keeping everything. It runs from monsters and never dies.
- A balloon over its head says what's wrong when it stops.

## 🎨 Edit my Bubie
Casing, hoodie and screen-light colours, 18 eyes and 10 mouths from the bubie.gg My Bubie editor, with a turning preview. Everyone on the server sees your look.

| | |
|---|---|
| ![The editor](https://media.forgecdn.net/attachments/2241/434/03-editor-png.png) | ![Another look in the editor](https://media.forgecdn.net/attachments/2241/439/08-editor_mint-png.png) |

![At night every screen glows in its own colour](https://media.forgecdn.net/attachments/2241/435/04-lineup_night-png.png)

## 🔌 Works with
- **Storage:** unloads into any block with an inventory: Sophisticated Storage, Functional Storage, Create vaults, Mekanism bins, chests (AE2: put a Storage Bus on its chest).
- **Power:** the station takes FE from any cable (Mekanism, Powah, Flux Networks, Ender IO, Pipez...), and the Bubie goes back to charge on its own, so it never runs out. Pipes can feed coal to the station, and any FE battery (Voltaic pocket accumulator, Mekanism energy tablet) in its fuel slot charges it without being used up.
- **Claims and teams:** breaks blocks as its owner, so FTB Chunks protection applies; FTB Teams teammates can give it orders.
- **Crops and ores:** Mystical Agriculture, Farmer's Delight and other standard crops; any ore in `c:ores` (All the Ores included); any modded tool.
- **Safe from automation:** mob capturers can't take it, and machines that attack as fake players don't hurt it.
- **Jade** shows its job, status and battery; **JEI/EMI** show the recipes.

Settings in `serverconfig/bubie-server.toml`.

---

## 🇧🇷 Português
O Bubie, mascote do bubie.gg, vira um ajudante: corta árvores inteiras e replanta, escava areia e terra, abre pedreiras com escada,
colhe e replanta plantações, minera veios ou túneis e junta itens do chão, guardando tudo no seu baú.
Com o mod Base Blueprint, ele também é **Construtor**: entregue a planta e ele levanta a base bloco por bloco, com o material do baú
(que outro Bubie pode ir enchendo).

- Marque a área com a **Prancheta do Bubie** (clique em 2 cantos, Shift + clique no baú), clique no Bubie com ela e dê uma ferramenta e um pouco de carvão.
- A **Estação do Bubie** carrega com FE de qualquer cabo ou combustível e é a casa dele. Com ela, ele nunca fica sem energia.
- **Editar meu Bubie:** cores do casco, do moletom e da luz da tela, olhos e boca, como no My Bubie do bubie.gg.
- Guarda em Sophisticated Storage, Functional Storage, cofres do Create e bins do Mekanism; respeita as áreas do FTB Chunks; amigos do mesmo time do FTB Teams também dão ordens.
- Se a vida acaba, ele desliga e vira o item, sem perder nada.

Made for the Bubie Pack. MIT license.
