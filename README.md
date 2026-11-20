<p align="center"><img src="src/main/resources/bubie_logo.png" width="128" alt="Bubie"></p>

# Bubie

Mod para **Minecraft 1.21.1 (NeoForge 21.1.265)**, feito para o Bubie Pack. O Bubie é o robozinho do bubie.gg
(tela CRT creme, olhos âmbar, moletom preto) como ajudante: segue você, carrega itens e, com uma tarefa e uma
área, coleta recursos sozinho e guarda tudo num baú.

## Imagens
| | |
|---|---|
| ![Três Bubies](docs/screenshots/portrait.png) | ![À noite o rosto brilha](docs/screenshots/night.png) |
| ![Lenhador](docs/screenshots/lumberjack.png) | ![Pedreira com escada](docs/screenshots/quarry.png) |
| ![Menu: tarefa](docs/screenshots/menu_job.png) | ![Menu: status](docs/screenshots/menu_status.png) |
| ![Construtor levantando uma base da Planta de Base](docs/screenshots/builder.png) | |

## Editar meu Bubie
Na aba **Status** do menu, **✎ Editar meu Bubie** abre o editor do My Bubie do bubie.gg dentro do jogo: cor do casco,
do moletom e da luz da tela, olhos e boca, com prévia girando (arraste para virar), **Aleatório** e **Padrão**.
As paletas e as peças de rosto são as do site (`features/my-bubie/manifest.ts`, `public/my-bubie/v1`), menos as cores do Plus.
A aparência fica salva no Bubie (e no item, quando ele é guardado ou desliga) e todos no servidor veem igual.
As expressões automáticas (feliz, trabalhando, erro, dormindo, machucado) aparecem por cima e voltam para o rosto escolhido.

| | |
|---|---|
| ![Editor](docs/screenshots/editor.png) | ![Bubies personalizados](docs/screenshots/lineup.png) |
| ![Editor com outro visual](docs/screenshots/editor_mint.png) | ![À noite, cada um com a sua luz](docs/screenshots/lineup_night.png) |

## Receitas
| Item | Receita |
|---|---|
| **Bubie** | cobre, vidro, cobre / cobre, redstone, cobre / _, lã preta, _ |
| **Prancheta do Bubie** | _, redstone, _ / papel, graveto, papel / papel, papel, papel |
| **Estação do Bubie** | cobre, vidro, cobre / redstone, baú, redstone / ferro, ferro, ferro |
| **Chips** | pepita de ouro e redstone em volta de: açúcar (velocidade), baú (armazenamento), bússola (alcance), funil (ímã), alto-forno (fundição), bloco de cobre (eficiência) |

Um Bubie novo já vem com a energia de um carvão.

## Como usar
| Ação | O que faz |
|---|---|
| Clique no chão com o item Bubie | Liga o Bubie. Ele passa a ser seu |
| Clique direito no Bubie | Abre o menu: modo, tarefa, área, status e o inventário dele |
| Shift + clique direito com a mão vazia | Guarda o Bubie no seu inventário, com tudo o que ele tem |
| Prancheta: clique em 2 blocos | Marca os cantos da área de trabalho (aparece no mundo em âmbar) |
| Prancheta: Shift + clique num baú | Marca onde ele guarda as coisas (qualquer bloco com inventário: baú, gaveta, Sophisticated Storage, AE2) |
| Prancheta: Shift + clique numa Estação | Marca a casa dele |
| Prancheta: clique no Bubie | Entrega área, baú e casa, e ele começa a trabalhar |
| Prancheta: Shift + clique no ar | Limpa a prancheta |
| Clique no Bubie com um Chip | Instala o chip |

**Modos:** Seguir (anda atrás de você e pega itens do chão), Ficar (senta e espera) e Trabalhar.

## Tarefas
| Tarefa | O que faz |
|---|---|
| **Lenhador** | Derruba a árvore inteira de uma vez e replanta a muda da mesma madeira. Não mexe em troncos sem folhas naturais nem em troncos encostados em tábuas, vidro ou baús (casas) |
| **Escavador** | Areia, cascalho, terra e argila, camada por camada (filtros no menu). Não mexe em areia suspeita |
| **Pedreira** | Escava a área inteira em camadas e deixa uma escada em espiral nas paredes. Antes de cavar perto de água ou lava, tampa com pedregulho que ele guarda |
| **Fazendeiro** | Colhe plantações maduras e replanta com as próprias sementes. Também cana, cacto, bambu, abóbora, melancia, cacau, fungo do Nether e frutas silvestres (sem quebrar o arbusto) |
| **Minerador** | Veio: minérios expostos na área (um veio inteiro, bloco a bloco). Túnel: galeria 1×2 no fundo da área com ramificações a cada 4 blocos, pegando todo minério que aparece e pondo tochas. Minérios pela tag `c:ores` (inclui All the Ores) |
| **Coletor** | Junta todos os itens do chão na área |
| **Construtor** (com o mod Base Blueprint) | Configure uma Planta de Base (forma, tamanho, material) e clique no Bubie com ela, onde a base deve ficar: ele constrói bloco por bloco, de baixo para cima, pegando os blocos no baú dele. A porta fica virada para você. Sem material, mostra "Esperando material" e continua quando o baú recebe mais, então outro Bubie (um Lenhador ou uma Pedreira) pode abastecer o mesmo baú enquanto ele constrói |

Sem área marcada, o Lenhador, o Fazendeiro e o Coletor trabalham em volta da Estação (raio 8).

## Regras
- Ele quebra blocos em nome do dono (um jogador falso com o seu UUID), então as proteções de terreno (FTB Chunks) valem para ele como para você.
- Usa a ferramenta do slot dele e gasta durabilidade. Fortuna e Toque Suave funcionam. Para quando a ferramenta está a 2 usos de quebrar.
- A picareta decide o que ele minera: com picareta de madeira ele não pega ferro (mostra "Precisa de ferramenta").
- Nunca cava o bloco embaixo de um baú, da estação ou de outro bloco com inventário.
- Quando enche, leva tudo ao baú (ou à Estação) e volta. Guarda para si sementes, mudas, pedregulho e tochas que ele usa. O carvão que encontra vai para o slot de combustível dele (até 16).
- Foge de monstros. Não morre: com a vida a zero ele **desliga** e vira o item, com inventário, ferramenta, chips e configurações. Não cai de altura e não se afoga.
- Problemas aparecem num balão sobre a cabeça (⚠ Sem baú, Precisa de ferramenta...) e com o rosto de erro.

## Energia e Estação
- Trabalhar gasta energia: um carvão dá 10 minutos. Coloque combustível no slot dele.
- A **Estação** é a casa: carrega o Bubie com FE (de qualquer mod) ou com combustível no slot dela, e guarda 27 pilhas. Com uma casa, ele volta para carregar quando a bateria baixa e para descansar à noite.
- Opcional (configuração do servidor): a Estação mantém os chunks em volta carregados.

## Chips (4 slots)
Velocidade (+50% minerar, +20% andar), Armazenamento (+9 espaços, até 36), Alcance (área maior), Ímã (pega itens a 5 blocos),
Fundição (minérios saem em barra) e Eficiência (−40% de energia). Chips iguais somam.

## Configuração (`serverconfig/bubie-server.toml`)
`maxPerPlayer` (3), `requireEnergy` (true), `maxEnergy`, `fuelMultiplier`, `fePerEnergy`, `breakSpeed` (0.75 = mais lento que você),
`areaSize` (32), `scanPerTick`, `restAtNight`, `stationLoadsChunks` (false), `teleportWhenStuck` (true), `homeRadius`.

## Compatibilidade
Testado com os mods do Bubie Pack carregados (`tools/prepare-pack-test.sh` e `./gradlew runPackGameTest`, 21 testes).

| Com | Como funciona |
|---|---|
| **Sophisticated Storage, Functional Storage, cofre do Create, bin do Mekanism, qualquer baú** | O Bubie guarda em qualquer bloco com inventário. Marque com Shift + clique da Prancheta. Ele usa a face voltada para ele (o Mekanism exige isso) |
| **AE2 e Integrated Dynamics** | Guarde num baú e ponha um Storage Bus nele (ou um Import Bus); o Bubie não fala com a rede direto |
| **Baterias (acumulador de bolso da Voltaic, tablet do Mekanism, baterias do Powah)** | No slot de combustível, recarregam o Bubie sem se gastar |
| **Máquinas da Voltaic (e outras)** | Marque a máquina como "baú" com a Prancheta: o Minerador entrega o minério bruto direto no pulverizador |
| **Mekanism, Powah, Flux Networks, Ender IO, Pipez, Voltaic** | A Estação recebe FE de qualquer cabo (até 4.000 FE/t, 4 FE por unidade de energia). Ligada na energia, o Bubie volta sozinho para carregar quando a bateria baixa: fica sempre ligado |
| **Canos, funis, esteiras** | Colocam carvão (ou melhor) direto no slot de combustível da Estação e tiram o que está guardado nela. Troncos e tábuas nunca são queimados |
| **FTB Chunks** | O Bubie quebra blocos como o dono, então só trabalha onde o dono pode mexer (opção "Allow Fake Players by Player ID", ligada no pack). Para ele trabalhar com você longe, carregue o chunk pelo FTB Chunks (ou ligue `stationLoadsChunks`) |
| **FTB Teams** | Quem é do time do dono também pode dar ordens, abrir o menu e editar o visual |
| **Mystical Agriculture, Farmer's Delight** | O Fazendeiro colhe e replanta plantas de mods que usam o bloco de plantação padrão; as que se colhem com clique direito (tomates do Farmer's Delight) são colhidas sem quebrar |
| **All the Ores e minérios de mods** | O Minerador usa a tag `c:ores` |
| **Mekanism Tools, Apotheosis e ferramentas de mods** | Qualquer ferramenta que funciona na mão do jogador funciona na dele, com encantamentos |
| **Ender IO (Soul Vial) e outros capturadores** | Não capturam o Bubie (`#c:capturing_not_supported`) |
| **Industrial Foregoing, Create** | Máquinas que atacam como jogador falso (triturador de mobs, deployer) não machucam o Bubie |
| **Jade** | Mostra tarefa, status, energia e dono |
| **JEI/EMI** | Mostram as receitas |

## Desenvolvimento
| Comando | O que faz |
|---|---|
| `./gradlew build` | Gera `build/libs/bubie-<versão>.jar` |
| `./gradlew runClient` | Abre o jogo com o mod |
| `tools/prepare-pack-test.sh` e `./gradlew runPackGameTest` | Os mesmos testes com os mods do Bubie Pack (Sophisticated, Functional Storage, Create, Mekanism, Mystical Agriculture, Farmer's Delight, All the Ores) |
| `./gradlew runGameTestServer` | Roda os testes de jogo (lenhador, fazendeiro, escavador, pedreira com água, minerador veio e túnel, ferramenta errada, desligar, combustível) |
| `./gradlew runShots` | Monta cenas e tira as screenshots de `run/screenshots` (só desenvolvimento) |
| `python tools/gen_textures.py` | Redesenha todas as texturas a partir da paleta do bubie.gg |

O plano do mod está em [docs/PLANO.md](docs/PLANO.md).

## Licença
[MIT](LICENSE).
