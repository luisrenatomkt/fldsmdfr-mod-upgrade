# Plano de testes

[AUTO] = executado em Java puro por `LogicSelfTest` (nao precisa de Minecraft). [MANUAL] = jogar.
Nenhum resultado manual deve ser considerado aprovado sem ter sido realmente observado no jogo.

| # | Teste | Como verificar |
|---|---|---|
| 1 | Instalacao/inicializacao [MANUAL] | Jar em `mods/`, Forge 1.8.9: mod "FLDSMDFR" listado, sem crash. |
| 2 | Colocacao [MANUAL] | Bloco na aba Redstone; o corpo aparece virado para voce. |
| 3 | Funcionamento inicial [MANUAL] | Painel abre; "Operacional"; luzes pulsando. |
| 4 | Um unico corpo central [MANUAL] | Em `/fldsmdfr evolve 2/8/20/60/200`, nao aparecem bracos, hastes, antenas, aneis, satelites, plataformas, torres ou modulos soltos; a casca do corpo incha, entorta, torce e abre cavidades. |
| 5 | Mutacoes deterministicas [AUTO] | Mesmo seed/tier produz o mesmo corpo; o indice de mutacao so muda a cada 0.35 de tier; uma nova mutacao deforma a casca. |
| 6 | Sem estagio maximo [AUTO] | Escala e altura continuam crescendo em tiers altos; apenas a quantidade de volumes desenhados e limitada a 20. |
| 7 | Abertura inferior [AUTO+MANUAL] | Auto: centro da abertura esta no fundo do corpo e o ponto de comida fica abaixo da casca. Manual: a boca, os shutters, os pistoes e o brilho ficam embaixo do corpo. |
| 8 | Descarga sempre para baixo [MANUAL] | Comida nasce logo abaixo da boca, com a parte de cima rente a ela, e cai; nao ha arco para cima. A dispersao horizontal e limitada. |
| 9 | Origem dinamica [MANUAL] | Apos `evolve 8/20/60`, a origem da comida acompanha o tamanho, a deformacao e o facing atuais do corpo. |
| 10 | Producao ligada ao crescimento [AUTO] | Mais componentes => mais producao (9,3 -> 410). |
| 11 | Instabilidade [MANUAL] | `instabilityRate 50`: barra sobe, faiscas, arcos na superficie e luzes piscam. |
| 12 | Colisao dinamica [MANUAL] | A colisao comeca acima da abertura; a comida nao fica presa dentro do bloco. A base do corpo bloqueia o jogador. |
| 13 | Tamanho da comida cresce [AUTO+MANUAL] | Auto: 0.7 -> 1.6 -> 3.5 -> 7.9. Manual: comparar apos `evolve 10` e `evolve 1000`. |
| 14 | Salvar/carregar [MANUAL] | Sair e reentrar: tempo, evolucao, instabilidade e formato do corpo mantidos. |
| 15 | Progressao offline [MANUAL] | Fechar 5+ min, reabrir: evolucao maior. |
| 16 | Chunks nao carregados [MANUAL] | Afastar-se: a logica pausa (so ocorre com o chunk carregado). |
| 17 | Limite de entidades [MANUAL] | `stormIntensity 100`: `maxActiveFood` nunca e excedido. |
| 18 | Valores muito altos [AUTO+MANUAL] | Auto: 10^500 componentes, 10^10000 em BigNum. Manual: `evolve 400`. |
| 19 | Funcionamento prolongado [AUTO+MANUAL] | Auto: 20 anos simulados sem NaN/Infinity. Manual: 1 h com `evolutionSpeed 100`. |
| 20 | Reinicio do jogo [MANUAL] | Fechar e abrir o Minecraft: estado recuperado. |
| 21 | Configuracoes [MANUAL] | `/fldsmdfr config launchDistance 100`, `visualComponents 0`, `foodSizeMultiplier 3`: efeitos correspondentes, sem crash. |
