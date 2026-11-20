# Bubie: plano do mod

O Bubie é um robozinho ajudante para Minecraft. Ele segue o jogador, guarda itens e, quando recebe uma tarefa, coleta recursos sozinho numa área: madeira, areia, terra, pedra, comida e minérios. Depois deixa tudo num baú.

Ele tem a aparência do mascote do bubie.gg, sem nenhuma peça extra.

## 1. O que já existe e vamos aproveitar

| O quê | Onde | Para que serve aqui |
|---|---|---|
| Bubie Pack: Minecraft **1.21.1 / NeoForge 21.1.265** | `~/www/minecraft-modpack` | Versão-alvo. O mod entra no pack como o terceiro mod próprio. |
| Base Blueprint | `~/www/base-blueprint` | Modelo do projeto: `build.gradle`, `gradle.properties`, GameTests e a busca de blocos no inventário, na mochila e em baús próximos. Essa busca serve para guardar os itens. |
| Voltaic Dynamics | `~/www/voltaic-dynamics` | Exemplo de FE, Jade e JEI/EMI. Serve para a estação de recarga. |
| Aparência padrão | `bubie/frontend/features/my-bubie/appearance.ts` (`DEFAULT_APPEARANCE`) | `shell_crt` ivory, `eyes_dots` amber, `mouth_neutral` amber, `top_hoodie` black |
| Rampas de cor | `bubie/frontend/features/my-bubie/manifest.ts` (`RAMP_KEYS`) | Cores exatas da textura (abaixo) |
| Sprites de referência | `bubie/frontend/public/my-bubie/v1/`, `bubie/frontend/docs/development/furniture-quality/bubie-scale.png` | Proporções do corpo |

Mods do pack que interessam: GeckoLib 4.9.3, Jade, JEI/EMI, Curios, Sophisticated Backpacks/Storage, FTB Chunks, FTB Ultimine e All the Ores.

## 2. Aparência

Ele mantém a forma do sprite, refeita em blocos no estilo do Minecraft:

- **Cabeça:** um monitor CRT creme, mais largo que alto e com fundo, maior que o corpo como no sprite. A frente tem uma tela escura rebaixada e um "queixo" com um LED âmbar.
- **Rosto na tela:** olhos de pontinho e boca reta em âmbar. Fica numa camada emissiva, então brilha no escuro. As expressões trocam só essa textura.
- **Corpo:** moletom preto com capuz caído nas costas, cordões e bolso canguru.
- **Braços, mãos, pernas e pés:** creme, curtos.
- **Tamanho:** cerca de 0,9 bloco de altura e 0,6 de largura. Passa por portas e túneis de 1×2.

### Paleta (copiada do site)

| Parte | Cores |
|---|---|
| Casco creme (`shell` ivory) | `#FFF9EE` luz, `#F3E7D5` base, `#DDCBB2` sombra, `#BFA586` sombra funda |
| Membros (ivory de 2 tons) | `#F7EEE1`, `#E2D2BC` |
| Brilho âmbar (`glow` amber) | `#FFE6B0`, `#F4BF69`, `#C98F3A` |
| Moletom preto (`cloth` black) | `#4C4547`, `#38333A`, `#29252B`, `#736B6C` (cordões), `#1B181E` (contorno) |
| Tela | cinza-grafite escuro, como no logo (cerca de `#2A2629`) |

### Expressões

| Estado | Rosto |
|---|---|
| Parado | olhos de pontinho, boca reta (padrão) |
| Feliz (carinho, tarefa concluída) | olhos `^^`, sorriso, como no `bubie-scale.png` |
| Trabalhando | olhos focados |
| Inventário cheio, sem ferramenta, sem baú | `404`, junto com um balão de aviso |
| Bateria fraca ou dormindo | olhos fechados, "z" piscando |
| Machucado | olhos `><` |

**Modelo:** fazer no Blockbench. Exportar como modelo Java do NeoForge, com animações em keyframes vanilla (`AnimationDefinition`), sem depender do GeckoLib. As animações são andar, golpe de ferramenta, sentar, acenar e desligar. Se elas ficarem limitadas, trocar para o GeckoLib (já está no pack). Fonte em `art-src/`, como nos outros mods.

## 3. Como o jogador usa

1. **Obter:** craftar um "Bubie desligado" (casco de vidro e cobre, placa de redstone, lã preta) e clicar com ele no chão. O Bubie liga e passa a ser seu (dono = UUID do jogador).
2. **Modos (clique com o botão direito abre o menu):**
   - **Seguir:** anda atrás do dono e pega itens soltos por perto.
   - **Ficar:** fica parado e não faz nada.
   - **Trabalhar:** executa a tarefa escolhida na área marcada.
3. **Área:** marcada pela **Prancheta do Bubie** (dois cliques, um em cada canto, com a prévia do contorno da Base Blueprint) ou por um raio em volta da estação de recarga.
4. **Baú de entrega:** com a prancheta, Shift + clique num baú ou numa gaveta. Funciona com qualquer bloco que tenha `IItemHandler`, incluindo Sophisticated Storage, gavetas e AE2 Interface.
5. **Ferramenta:** um slot de ferramenta. O Bubie usa a ferramenta dada e gasta durabilidade. O nível da picareta limita o que ele minera. Fortune e Silk Touch valem, Mending conserta. Antes de quebrar, ele para de usar a ferramenta para não perdê-la.

### Menu

Abas **Tarefa** (modo e tarefa, com filtros), **Inventário** (18 slots, ferramenta, upgrades), **Área** (tamanho, baú ligado, "mostrar no mundo") e **Status** (energia, vida, o que está fazendo agora).

## 4. Tarefas

| Tarefa | O que faz | Detalhes |
|---|---|---|
| **Lenhador** | Corta a árvore inteira (tronco e folhas penduradas) e replanta a muda | Usa as tags `minecraft:logs` e `#c:saplings`, então funciona com árvores de outros mods. Não toca em troncos com blocos colocados por jogadores em volta (proteção contra quebrar casas). |
| **Escavador** | Areia, cascalho, terra e argila | Escava camadas de cima para baixo. Filtro de blocos na aba Tarefa. |
| **Pedreira** | Pedra, deepslate, granito e outras pedras numa área | Desce em camadas e deixa uma escada. Antes de escavar, cobre lava e água com o que tiver. Nunca cava o bloco embaixo de si. |
| **Fazendeiro (comida)** | Colhe plantações maduras e replanta | Tags `#c:crops` e `CropBlock`. Inclui cana, abóbora, melancia e frutinhas. Mais tarde: tirar leite e cuidar de animais. |
| **Minerador** | Minérios | Dois jeitos: **veio** (minera minérios expostos numa área, o veio inteiro) e **túnel** (galerias em espinha de peixe numa altura escolhida). Tag `#c:ores`, então pega os minérios do All the Ores. |
| **Coletor** | Junta itens soltos na área e guarda | Útil perto de fazendas automáticas. |

Regras de todas as tarefas:

- A quebra é feita por um **FakePlayer em nome do dono**. Isso aplica as proteções do FTB Chunks, o `BlockEvent.BreakEvent` e a loot table com a ferramenta certa.
- Quebrar leva tempo, como para o jogador, com a animação de rachadura. Em média ele é mais lento que o jogador, para não desequilibrar o jogo.
- Quando o inventário enche, ele vai ao baú, guarda tudo e volta. Se não tiver baú, para e mostra `404`.

## 5. Energia, vida e upgrades

- **Energia:** bateria interna. Recarrega com carvão, carvão vegetal ou blocos de redstone no slot de combustível, ou na **Estação do Bubie**, que aceita FE (Voltaic, Mekanism, Powah). Ele trabalha cerca de 10 minutos com um carvão, ajustável na configuração.
- **Estação do Bubie:** um bloco que serve de casa. Ele volta para ela à noite ou com bateria fraca. A estação pode manter o chunk carregado (opção da configuração, desligada por padrão no servidor).
- **Vida:** 20. Ele não luta: foge de monstros e volta ao trabalho depois. Ao chegar a 0 ele **desliga** e vira o item "Bubie desligado" com o inventário e as configurações guardados. Nunca some de vez.
- **Chips de upgrade** (4 slots): velocidade, inventário maior, alcance da área, ímã de itens, fundição automática (minério sai em barra, gastando energia extra) e mais uma tarefa ao mesmo tempo.

## 6. Arquitetura técnica

```
com.bubie
├── Bubie.java                  // @Mod, registros
├── entity/BubieEntity.java     // PathfinderMob + OwnableEntity, inventário, energia, NBT
├── entity/ai/                  // Goals: Follow, Stay, Flee, ReturnHome, Deposit, Pickup
├── job/                        // Job (interface) + LumberJob, DigJob, QuarryJob, FarmJob, MineJob, CollectJob
│   └── JobRunner.java          // máquina de estados: procurar alvo → andar → quebrar → coletar → guardar
├── work/BlockBreaker.java      // progresso de quebra, FakePlayer, loot, durabilidade
├── work/AreaScanner.java       // busca de alvos com limite de blocos por tick e cache
├── storage/Deposit.java        // IItemHandler, reaproveitando a lógica do Base Blueprint
├── item/                       // Bubie desligado, Prancheta, Chips
├── block/BubieStation*         // bloco, BlockEntity, FE
├── menu/ + client/screen/      // menu com abas
├── client/model/, renderer/    // modelo, camada emissiva do rosto, expressões
├── network/                    // payloads da prancheta e do menu
└── gametest/                   // testes de cada tarefa
```

Pontos de atenção:

- **Desempenho:** a busca de alvos é dividida entre ticks (um limite de blocos por tick) e o alvo fica guardado até ser quebrado. Um limite de Bubies por jogador na configuração (padrão 3).
- **Chunks descarregados:** ele só trabalha enquanto o chunk está carregado, a não ser que a estação o mantenha carregado.
- **Caminhos:** a navegação no chão do vanilla serve para seguir e para a superfície. Na pedreira e no minerador, ele cava o próprio caminho (escada e túnel de 1×2) em vez de depender do pathfinding.
- **Multiplayer:** só o dono (e o time no FTB Teams, opcional) abre o menu e dá ordens.
- **Idiomas:** `pt_br` e `en_us` desde o começo, como o pack.

## 7. Fases

| Fase | Entrega | Pronto quando |
|---|---|---|
| **0. Projeto** | Copiar a estrutura do Base Blueprint. `mod_id=bubie`, `com.bubie`, licença MIT, `git init` | `gradlew runClient` abre o jogo com o mod carregado |
| **1. O Bubie existe** | Entidade, modelo, textura, rosto emissivo, andar, Seguir/Ficar, inventário, menu, item "desligado" | Ele aparece no jogo igual ao mascote, segue o dono e guarda itens |
| **2. Lenhador + baú (MVP)** | Prancheta, área, `JobRunner`, `BlockBreaker`, guardar no baú, replantar | Ele limpa uma floresta de 16×16, replanta e guarda tudo no baú. GameTest passando |
| **3. Escavador e Pedreira** | Escavação em camadas, segurança com lava e água, escada | Ele escava 16×16×10 sem morrer e sem deixar buracos perigosos |
| **4. Fazendeiro** | Colher e replantar | A fazenda de trigo, cenoura e batata fica sempre replantada |
| **5. Minerador** | Veio e túnel, limite pelo nível da picareta | Ele minera os minérios do All the Ores e para quando a ferramenta vai quebrar |
| **6. Estação, energia, upgrades** | Bloco, FE, combustível, chips, voltar para casa | Ele trabalha sozinho de dia e recarrega à noite |
| **7. Capricho** | Expressões, sons (bipes), partículas, balões de aviso, Jade, receitas no JEI/EMI | Uma partida com amigos no pack sem nada estranho |
| **8. No pack** | Quests no `gen_quests.py`, entrada no `manifest.json`, página no CurseForge | Publicado no CurseForge e no Bubie Pack |

O MVP para testar com os amigos é das fases 0 a 2.

## 8. Decisões (tomadas em 2026-11-20, as recomendadas)

1. **Como conseguir:** craft no início do jogo (cobre, vidro, redstone, lã preta). A quest de prêmio fica para quando o mod entrar no pack.
2. **Energia:** ligada, com `requireEnergy` na configuração para desligar. Um Bubie novo vem com a energia de um carvão.
3. **Quantidade:** até 3 por jogador (`maxPerPlayer`).
4. **Modelo:** vanilla, sem GeckoLib. O mod não depende de nenhum outro; o Jade é opcional.
5. **Nome:** "Bubie".

## 9. Situação (2026-11-20)

Fases 0 a 7 feitas, versão 0.1.0:

- Entidade, modelo, texturas (geradas por `tools/gen_textures.py` com a paleta do site), rosto emissivo com 6 expressões e piscada, balão de problema sobre a cabeça.
- Seguir, Ficar, Trabalhar; menu com abas Tarefa, Área e Status; Prancheta com prévia no mundo; guardar o Bubie com Shift + clique; desligar em vez de morrer.
- As 6 tarefas, guardar no baú com tampa abrindo, regras de segurança (água e lava, blocos embaixo de baús, casas, ferramenta quase quebrando).
- Estação (FE ou combustível, 27 espaços, casa, descanso à noite, chunk loading opcional), 6 chips, energia, configuração do servidor.
- Jade, 9 GameTests passando, screenshots automáticas.

Mudanças em relação ao plano:

- O chip "mais uma tarefa ao mesmo tempo" virou o **Chip de eficiência** (−40% de energia): duas tarefas na mesma área confundiam a ordem de trabalho.
- As abas são só do painel esquerdo; o inventário fica sempre visível à direita.
- Só carvão vai sozinho para o slot de combustível. Os primeiros testes mostraram o Lenhador queimando a própria madeira.
- Sons: notas do bloco musical (bipes), sem arquivos de som próprios.

Falta (fase 8, depende de você): publicar no CurseForge e adicionar ao Bubie Pack (`manifest.json` precisa do id do projeto no CurseForge) com uma quest que dá o primeiro Bubie.
