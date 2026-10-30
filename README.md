# FLDSMDFR - Maquina de Comida Infinita (Minecraft 1.8.9 / Forge 11.15.1.2318)

Mod inspirado na maquina FLDSMDFR do filme "Ta Chovendo Hamburguer". Versao 1.1.0.

## O que muda com a evolucao

* **Um unico corpo central.** A maquina nao cria bracos, hastes, antenas, cabos, aneis orbitais, satelites,
  plataformas, torres ou modulos soltos ao redor. Toda a evolucao acontece no proprio corpo: a casca deforma,
  incha, entorta, torce, afunda, abre cavidades e funde camaras e mecanismos embutidos nela.
* **Mutacoes deterministicas e quantizadas.** O formato e uma funcao pura de `(seed, tier)`. A cada
  `MachineBody.MUTATION_STEP = 0.35` de tier o indice de mutacao muda e a geometria recebe uma nova deformacao.
  Nao ha sorteio a cada frame e o formato e identico depois de salvar/carregar o mundo.
* **Sem estagio maximo artificial.** O corpo continua crescendo e mudando para sempre. O tier e
  `log10(1 + evolucao)` e a escala e `1 + 0.50 * ln(1 + tier)`, entao o tamanho cresce de forma logaritmica e
  controlada, sem explodir o mundo.
* **Detalhes desenhados com limite, logica sem limite.** A quantidade de volumes de mutacao desenhados e
  limitada a 20 (`MachineBody.MAX_FEATURES`) para manter o custo baixo. A quantidade logica de componentes,
  producao e lancamento continua sem limite.
* **Eras infinitas:** a cada 6 "decadas" de evolucao comeca uma nova era, com novo esquema de cores. Os nomes
  das 12 primeiras seguem a imagem original (Original, Aprimoramento, Conectividade, Expansao, Propulsao,
  Defesa e poder, Infinito, Sem limites, Dominio total, Onipresenca, Transcendencia, Infinito absoluto); depois
  disso, "O Criador" para sempre.

## Comida e descarga

* **Toda comida sai pela abertura inferior animada.** O ponto de origem e recalculado a partir das dimensoes
  atuais do corpo, da deformacao, do facing e da animacao da abertura. A comida sempre nasce logo abaixo do
  corpo, com a parte de cima rente a boca, e cai para baixo.
* **Nao existe lancamento para cima.** A velocidade vertical e sempre negativa; `launchDistance` controla
  apenas a dispersao horizontal (limitada) para a comida nao cair toda no mesmo bloco.
* **Comida fisica 3D (nao e item, nao pode ser pega):** hamburguer, pizza, rosquinha, bolo, macarrao,
  cachorro-quente, sanduiche, batata frita e sorvete. As entidades sao solidas, quicam, ficam no chao e
  desaparecem depois de `foodLifespanTicks`. O tamanho desenhado cresce com a evolucao, sem limite; a caixa de
  colisao da entidade e limitada a 6 blocos de largura por 3,7 de altura por motivo de performance.
* A abertura tem shutters, pistoes internos e brilho que seguem o mesmo ciclo usado pelo servidor para
  disparar os lotes de comida.

## Como jogar

* Receita: `I G I / R C R / I H I` (I = bloco de ferro, G = vidro, R = redstone, C = bolo, H = funil).
  Criativo: aba Redstone.
* Clique direito: painel de informacoes (mostra escala, altura real do corpo e "Descarga: abertura inferior
  animada"). Shift + clique direito com a mao vazia: liga/desliga.
* `/fldsmdfr config [chave valor]`, `/fldsmdfr info`, `/fldsmdfr evolve <expoente>` (teste: evolucao =
  10^expoente), `/fldsmdfr surge`. Comandos exigem cheats (ou edite `config/fldsmdfr.cfg`).
  Dica: `evolutionSpeed 0.2` deixa tudo mais lento.

## Configuracoes principais (`config/fldsmdfr.cfg`)

| Chave | Padrao | Efeito |
|---|---|---|
| `evolutionSpeed` | 1.0 | Multiplicador da velocidade de evolucao. |
| `productionIntensity` | 1.0 | Multiplicador da producao de comida. |
| `stormIntensity` | 1.0 | Multiplicador dos lotes, surtos e frequencia de tempestade. |
| `instabilityRate` | 1.0 | Multiplicador do crescimento da instabilidade. |
| `launchDistance` | 48.0 | Dispersao horizontal base da descarga (a escala do corpo tambem influencia). |
| `foodSizeMultiplier` | 1.0 | Multiplicador do tamanho fisico da comida. |
| `maxActiveFood` | 150 | Maximo de entidades de comida vivas por mundo. |
| `simulationDistance` | 96.0 | Distancia maxima de um jogador para a comida ser spawnada. |
| `updateInterval` | 2 | Ticks entre atualizacoes da logica. |
| `syncInterval` | 20 | Ticks entre sincronizacoes do snapshot para o cliente. |
| `offlineProgress` | true | Aplica progresso logico enquanto o mundo estava fechado. |
| `maxOfflineHours` | 72.0 | Maximo de horas de progresso offline. |
| `visualEffects` | true | Faiscas, fumaca e arcos. |
| `sounds` | true | Sons mecanicos. |
| `maxLaunchesPerUpdate` | 6 | Maximo de entidades spawnadas por ciclo de descarga. |
| `visualComponents` | 20 | Maximo de volumes de mutacao desenhados (0..20, limite raso de detalhe). |
| `foodLifespanTicks` | 3600 | Ticks antes da comida no chao desaparecer. |

## Arquitetura (pacote `com.fldsmdfr`)

| Classe | Responsabilidade |
|---|---|
| `core.MachineBody` | Forma deterministica do unico corpo central: perfil, deformacoes, mutacoes quantizadas, volumes embutidos, abertura inferior e animacao da descarga. |
| `core.MachineState` | Estado persistente e simulacao logica (evolucao, producao, instabilidade, surtos, offline). |
| `core.BigNum` | Numero mantissa x 10^expoente(long): evolucao e producao nunca estouram. |
| `core.MachineModel` | Regras puras de aparencia: tier, escala, eras, cores, tamanho da comida e conversao local -> mundo. |
| `core.MachineStats` | Producao/lancamento a partir da quantidade de componentes; composicao muda a cada era. |
| `core.LaunchSystem` | Descarga exclusivamente inferior: origem na abertura, velocidade vertical negativa e dispersao horizontal controlada. |
| `entity.EntityFoodDrop` | Comida fisica (tipo e tamanho no DataWatcher), nao e salva no chunk. |
| `client.Gfx` | Gravador de geometria (pilha de matrizes + primitivas): maquina inteira em uma chamada de desenho. |
| `client.RenderCore` | Desenha o corpo central, a casca deformada, os volumes embutidos, a tela/teclado e a abertura inferior. |
| `client.RenderFood` / `FoodModels` | Comidas desenhadas por codigo. |
| `tile.TileEntityFldsmdfr` | Orquestra no servidor; o cliente recebe so um snapshot pequeno (`MachineView`). |
| `block.BlockCore` | Colisao dinamica que comeca acima da abertura, para a comida cair livremente. |

### Como os sistemas se interligam

1. A evolucao `E` cresce exponencialmente e acelera (taxa depende do tier, da capacidade e da instabilidade).
2. `E` define o numero de componentes `n = 4 + 2*E^0.45`; so uma amostra deles e desenhada (nivel de detalhe),
   a quantidade total continua na logica.
3. Producao e lancamento vem de `n`; o tamanho do corpo e da comida vem do tier `T = log10(1+E)`.
4. Instabilidade cresce com o tamanho: vibracao, luzes piscando, arcos eletricos na superficie, surtos de
   superproducao e descargas mais frequentes.

## Build e testes

Requisitos: JDK 17 para o Gradle/Loom (o mod compila para Java 8) e Gradle 7.6.4.

```bash
gradle build --no-daemon
```

O `LogicSelfTest` cobre a logica independente de Minecraft (BigNum, stats, escala, eras, corpo central,
mutacoes deterministicas, abertura e simulacao longa). O desenho, a colisao e a fisica dentro do Minecraft
precisam ser verificados jogando; veja `docs/TESTING.md`.

## Limitacoes

* A logica pura e a simulacao longa sao executadas fora do Minecraft; o desenho e a fisica so podem ser
  verificados no jogo.
* Apenas 20 volumes de mutacao sao desenhados; a quantidade logica de componentes pode ser muito maior.
* Numero de comidas ativas limitado por `maxActiveFood` (padrao 150); as mais antigas somem.
* A caixa de colisao da comida e limitada a 6 blocos de largura e 3,7 de altura (o modelo desenhado continua crescendo).
* A colisao do bloco e um unico AABB dinamico. Como o Minecraft 1.8.9 consulta colisao apenas nos blocos
  proximos da entidade, ela e eficaz na base do corpo; nao ha blocos multibloco.
* A comida e decorativa/fisica: nao causa dano e nao e salva ao fechar o mundo.
* Quebrar o bloco apaga os dados da maquina. A progressao offline so ocorre quando o chunk e recarregado.
* Sem GUI de configuracao em jogo: use comando ou arquivo.
