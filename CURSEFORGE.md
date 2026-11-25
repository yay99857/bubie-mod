# Bubie

**A little CRT robot that works for you: it chops trees, digs sand, quarries stone, farms and mines, then puts everything in your chest.**

*🇧🇷 Versão em português mais abaixo.*

![Seven Bubies, each dressed up in the editor](https://media.forgecdn.net/attachments/2241/432/01-lineup-png.png)

Bubie is a cream CRT monitor in a black hoodie, as a helper for **Minecraft 26.3 and 1.21.1 (NeoForge)**, plus an older version for 1.20.1 (Forge). Craft one, click the ground to switch it on, and give it a job.

![The default Bubie up close](https://media.forgecdn.net/attachments/2241/433/02-portrait-png.png)

## 🤖 What it does
- **Follow:** walks after you and picks up items on the ground. **Stay:** sits and waits. **Work:** does its job in an area.
- **Lumberjack:** fells whole trees and replants the same sapling. Leaves your log cabins alone.
- **Digger:** sand, gravel, dirt and clay, layer by layer (filters in the menu).
- **Quarry:** digs out the whole area and leaves a staircase on the walls. Plugs water and lava with cobblestone first.
- **Farmer:** harvests and replants crops; sugar cane, cactus, bamboo, pumpkins, melons, cocoa, nether wart, sweet berries.
- **Miner:** exposed ore veins, or branch tunnels with torches. Uses the `c:ores` tag, so modded ores count.
- **Collector:** gathers every item lying in the area.
- **Builder** (with Base Blueprint): hand it a configured blueprint where the base should go and it builds it block by block, taking the blocks from its chest. What is left to place shows as see-through blocks: aim at one to see which block goes there, and place blocks yourself to help. Out of blocks, it waits and says which block it needs; another Bubie (a Lumberjack or a Quarry) can keep that chest filled while it builds. With Base Blueprint 1.4.0+, set **Built by: Bubie** in the blueprint menu and right-click a block: your nearest Bubie builds the base on the previewed spot.
- **Guard:** fights the monsters that walk into its area with the weapon you give it, picks up their drops and keeps watch at night (creepers are left alone).
- **Courier:** empties the chests, hoppers and furnace outputs in its area into its own chest. Feeding a Builder, it carries only what the blueprint still needs.

Several Bubies in one area split the work: each one claims the block (or, for lumberjacks, the whole tree) it is on.

**Makes its own way:** when no path leads to where it must work or to its chest, it plans one and changes the terrain a block at a time, as a player would: digs down a narrow pit, cuts steps up or down a wall, tunnels, bridges a gap, and climbs out when the job is done. Only natural ground, never anything built, and every hole it had to open is filled back in afterwards. Truly stuck, it shows a **?** on its screen.

![A quarry with its staircase](https://media.forgecdn.net/attachments/2241/438/07-quarry-png.png)

## 🏠 The Station
Its menu has a **tracker**: each Bubie living there with its face, job, status, battery, coordinates and the distance and direction to it, also when it is far away in unloaded chunks. The **Wireless Station** shows the same tracker in your hand, from anywhere: Shift + click a Station to link it.

## 🧩 Program Chip (optional progression)
Servers and packs can turn on `advancedJobsNeedChip`: then the Miner, Builder, Courier and Guard need a Program Chip. Off by default.

Languages: English, Português (Brasil), Español.

## 🎶 Bubie the DJ
Any Bubie can play music for everyone nearby, whatever job it has. Open its menu, **DJ** tab:
- It plays the **music disc** in the DJ tab's slot (from any mod), over and over while it is switched on.
- Play / stop and volume; the song title floats over its head.
- Its screen turns into an **equalizer**, it dances and sends out notes, and the Bubies sitting in Stay mode within 10 blocks dance along.
- The sound follows the Bubie as it walks, like a jukebox on legs.


## 📋 How to use it
1. Craft the **Bubie Clipboard** and Shift + click a chest.
2. Click your Bubie with the clipboard. It works **around that chest** (radius 8, set with − / + in its menu): the Lumberjack finds the trees nearby, the Farmer the fields. The Quarry and the Digger open a pit in front of the chest, never under your base. Want an exact area? Click its two corners with the clipboard first.
3. Give it a tool (it uses your axe, shovel or pickaxe, and stops before it breaks) and some redstone.

| | |
|---|---|
| ![The Job tab](https://media.forgecdn.net/attachments/2241/436/05-menu_job-png.png) | ![The Status tab](https://media.forgecdn.net/attachments/2241/437/06-menu_status-png.png) |

## ⚡ Energy, home and chips
- It is a robot: it runs on redstone (one dust = 5 minutes of work, a block = 45) or FE, not coal. The **Bubie Station** charges it with FE or redstone, stores what it brings back, and is where it rests at night. Its menu has a **tracker**: where each Bubie living there is and what it is doing, and the **Wireless Station** shows the same in your hand, from anywhere.
- **Drives:** what a Bubie carries lives on its drive. Every Bubie comes with a **Hard Drive** (18 slots); a **Solid-State Drive** gives it 36. Take the drive out and the cargo goes with it, into another Bubie or your pocket; its tooltip lists what is on it.
- Four chip slots: Speed, Range, Magnet, Smelting and Efficiency.
- At 0 health it **shuts down** into its item, keeping everything. It runs from monsters and never dies.
- A balloon over its head says what's wrong when it stops.
- Hold **Alt + B** to see your own Bubies through walls, outlined in amber (any key you like in Controls).
- **Name it** from its menu: click its name over the storage (a name tag works too).

## 🎨 Edit my Bubie
Casing, hoodie and screen-light colours, 18 eyes and 10 mouths, with a turning preview. Everyone on the server sees your look.

| | |
|---|---|
| ![The editor](https://media.forgecdn.net/attachments/2241/434/03-editor-png.png) | ![Another look in the editor](https://media.forgecdn.net/attachments/2241/439/08-editor_mint-png.png) |

![At night every screen glows in its own colour](https://media.forgecdn.net/attachments/2241/435/04-lineup_night-png.png)

## 🔌 Works with
- **Storage:** unloads into any block with an inventory: Sophisticated Storage, Functional Storage, Create vaults, Mekanism bins, chests (AE2: put a Storage Bus on its chest).
- **Power:** the station takes FE from any cable (Mekanism, Powah, Flux Networks, Ender IO, Pipez...), and the Bubie goes back to charge on its own, so it never runs out. Pipes can feed redstone to the station, and any FE battery (Voltaic pocket accumulator, Mekanism energy tablet) in its fuel slot charges it without being used up.
- **Claims and teams:** breaks blocks as its owner, so FTB Chunks protection applies; FTB Teams teammates can give it orders.
- **Crops and ores:** Mystical Agriculture, Farmer's Delight and other standard crops; any ore in `c:ores` (All the Ores included); any modded tool.
- **Safe from automation:** mob capturers can't take it, and machines that attack as fake players don't hurt it.
- **Jade** shows its job, status and battery; **JEI/EMI** show the recipes.

Settings in `serverconfig/bubie-server.toml`.

## 📦 Versions
| Minecraft | Loader | Bubie | Notes |
|---|---|---|---|
| **26.3** | NeoForge | 2.0.0 | Newest |
| **1.21.1** | NeoForge | 2.0.1 | Same features; the Bubie Pack's version |
| 1.20.1 | Forge | 1.5.3 | Older feature set, bug fixes only |

The Builder needs **Base Blueprint** for the same Minecraft version.

---

## 🇧🇷 Português
O Bubie, um robozinho de tela CRT e moletom preto, é um ajudante: corta árvores inteiras e replanta, escava areia e terra, abre pedreiras com escada,
colhe e replanta plantações, minera veios ou túneis e junta itens do chão, guardando tudo no seu baú.
Com o mod Base Blueprint, ele também é **Construtor**: entregue a planta e ele levanta a base bloco por bloco (o que falta aparece como blocos fantasma, e você pode ajudar colocando), com o material do baú
(que outro Bubie pode ir enchendo).

- Shift + clique num baú com a **Prancheta do Bubie** e clique no Bubie: ele trabalha em volta do baú (o raio se ajusta no menu). Dê uma ferramenta e um pouco de redstone. Para uma área exata, marque 2 cantos antes.
- A **Estação do Bubie** carrega com FE de qualquer cabo ou combustível e é a casa dele. Com ela, ele nunca fica sem energia. O menu dela tem um **Rastreador** (onde está cada Bubie e o que está fazendo), e a **Estação Sem Fio** mostra o mesmo na sua mão, de qualquer lugar.
- **Editar meu Bubie:** cores do casco, do moletom e da luz da tela, olhos e boca.
- **Guarda:** enfrenta os monstros da área com a arma que você der, junta o que eles largam e vigia à noite.
- **Bubie DJ:** na aba **DJ** do menu, ele toca o disco de música do slot da aba para todo mundo por perto. A tela vira um equalizador, ele dança e os Bubies em modo Ficar em volta dançam junto.
- Guarda em Sophisticated Storage, Functional Storage, cofres do Create e bins do Mekanism; respeita as áreas do FTB Chunks; amigos do mesmo time do FTB Teams também dão ordens.
- Se a vida acaba, ele desliga e vira o item, sem perder nada.
- Ele **abre o próprio caminho**: quando não tem como chegar, planeja e cava um poço, faz degraus, túnel ou ponte, sai do buraco no fim e tapa o que abriu. Preso de verdade, mostra um **?** na tela.
- Segure **Alt + B** para ver seus Bubies através das paredes, com contorno âmbar.
- **Discos:** o que o Bubie carrega fica gravado no disco dele. Todo Bubie vem com um **HD** (18 espaços); um **SSD** dá 36. Tirou o disco, a carga vai junto.
- **Dê um nome** pelo menu: clique no nome dele, em cima do inventário.
- Versões: **Minecraft 26.3** e **1.21.1** (NeoForge), e uma mais antiga para 1.20.1 (Forge).

Made for the Bubie Pack. MIT license.
