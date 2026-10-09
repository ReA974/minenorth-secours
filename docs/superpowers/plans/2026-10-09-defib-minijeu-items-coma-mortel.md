# Défibrillateur mini-jeu, vrais items, coma mortel, soins complets — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter 3 vrais items de soin (dont un défibrillateur 3D avec mini-jeu de rythme), rendre le coma mortel avec réveil à l'hôpital, et faire rendre tous les cœurs aux soins.

**Architecture:** Un `CareItem` unique (type = BANDAGE/TROUSSE/DEFIB) remplace la détection par id vanilla. La validation du mini-jeu vit dans une classe pure `DefibScore` (testable sans Minecraft) ; le serveur génère les battements depuis une graine, le client (`DefibScreen`) les affiche et renvoie ses frappes, le serveur recalcule la précision. La mort en coma passe par `LivingDeathEvent` + `PlayerRespawnEvent`.

**Tech Stack:** Forge 1.20.1 (47.4.10), Java 17, JUnit 5 (à ajouter), SimpleChannel.

**Spec:** `docs/superpowers/specs/2026-10-09-defib-minijeu-items-coma-mortel-design.md`

## Global Constraints

- Mod id `minenorthsecours`, paquet `fr.minenorth.secours`, textes joueur en français.
- Mini-jeu : 12 battements ; `defib_precision_min` = 0.7 ; `coma_mortel` = true par défaut.
- Mort en coma applicable aussi aux joueurs « exempts ».
- Réanimation et trousse : `setHealth(getMaxHealth())` ; le bandage ne rend pas de vie.
- Les champs `item` de la config disparaissent ; un ancien JSON qui les contient doit se charger sans erreur.
- Défibrillateur non consommé en cas d'échec du mini-jeu.
- Bandage et trousse gardent la barre de progression (`Care`).

## Review Focus

- Frappes en rafale (plus de 2× le nombre de battements) : précision 0, pas de réanimation.
- Zéro frappe / écran fermé tout de suite : échec, défibrillateur gardé.
- Ancien `config/minenorth_secours.json` avec `"item": "minecraft:paper"` : chargement OK.
- Joueur en coma tué sans point d'hôpital défini (`hasHospital` faux) : respawn normal, pas d'exception.
- Secouriste ou patient qui se déconnecte pendant le mini-jeu : session nettoyée.

## Structure des fichiers

- Create `src/main/java/fr/minenorth/secours/DefibScore.java` — logique pure du mini-jeu.
- Create `src/test/java/fr/minenorth/secours/DefibScoreTest.java` et `SecoursConfigTest.java`.
- Create `src/main/java/fr/minenorth/secours/item/CareItem.java` — item de soin.
- Create `src/main/java/fr/minenorth/secours/client/DefibScreen.java` — écran du mini-jeu.
- Create `tools/GenAssets.java` — génère les textures PNG.
- Create `src/main/resources/assets/minenorthsecours/models/item/{bandage,trousse_soins,defibrillateur}.json` et `textures/item/*.png`.
- Modify `build.gradle`, `ModItems.java`, `SecoursConfig.java`, `SecoursService.java`, `ModNetwork.java`, `ClientNetworkHandler.java`, `lang/fr_fr.json`, `lang/en_us.json`, `README.md`.

---

### Task 1: DefibScore (logique pure) + JUnit

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/fr/minenorth/secours/DefibScore.java`
- Test: `src/test/java/fr/minenorth/secours/DefibScoreTest.java`

**Interfaces:**
- Produces:
  - `static long[] DefibScore.beats(long seed, int count)` — décalages en ms depuis l'ouverture de l'écran, strictement croissants, premier >= 1500, écart entre battements dans [550, 900].
  - `static double DefibScore.evaluate(long[] beats, long[] taps, long elapsedMs)` — précision 0..1.

- [ ] **Step 1: Dans `build.gradle`**, ajouter `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'` aux dépendances et un bloc `test { useJUnitPlatform() }`.

- [ ] **Step 2: Écrire `DefibScoreTest`** (JUnit 5) avec ces tests :
  - `beatsAreDeterministic` : `beats(42,12)` égal à `beats(42,12)`, longueur 12.
  - `beatsAreIncreasingWithBoundedGaps` : premier >= 1500, chaque écart entre 550 et 900 inclus.
  - `perfectTapsScoreOne` : taps = beats, elapsed = dernier battement + 1000 → `assertEquals(1.0, ..., 1e-9)`.
  - `noTapsScoreZero` : `new long[0]` → 0.0.
  - `goodTapsScoreHalf` : chaque tap décalé de +150 ms → 0.5.
  - `lateTapsScoreZero` : décalés de +400 ms → 0.0.
  - `spamIsRejected` : 25 frappes (> 2×12) dont 12 parfaites → 0.0.
  - `tooFastIsRejected` : taps parfaits mais `elapsedMs` = dernier battement − 600 → 0.0.
  - `oneTapCannotMatchTwoBeats` : une seule frappe sur le premier battement → 1/12.

- [ ] **Step 3: Lancer** `./gradlew test --tests '*DefibScoreTest'` — attendu : échec de compilation (classe absente).

- [ ] **Step 4: Implémenter `DefibScore`** (classe `final`, sans import Minecraft). `beats` : `Random(seed)`, premier = 1500 + nextInt(300), puis écart 550 + nextInt(351). `evaluate` : renvoie 0 si `taps.length > 2 * beats.length` ou `elapsedMs < dernierBattement - 500`. Sinon chaque battement s'apparie à la frappe non utilisée la plus proche : |écart| <= 80 ms = 1.0, <= 180 ms = 0.5, sinon 0 ; résultat = moyenne sur tous les battements.

- [ ] **Step 5: Relancer le test** — attendu : tous PASS.

- [ ] **Step 6: Commit** `feat: DefibScore, validation du mini-jeu de defibrillation`.

---

### Task 2: Vrais items, textures, modèle 3D, onglet créatif, config

**Files:**
- Create: `item/CareItem.java`, `tools/GenAssets.java`, 3 modèles JSON, 3 textures PNG, `src/test/.../SecoursConfigTest.java`
- Modify: `item/ModItems.java`, `config/SecoursConfig.java`, `SecoursService.java` (`careType`, `objet`), `lang/fr_fr.json`, `lang/en_us.json`

**Interfaces:**
- Produces : `CareItem(int type, int stack)` avec `public final int type` ; `ModItems.BANDAGE_ITEM`, `ModItems.TROUSSE_ITEM`, `ModItems.DEFIB_ITEM` (`RegistryObject<Item>`) ; `SecoursConfig.Objet(int secondes, boolean consomme, boolean reserveSecours)` sans champ `item` ; nouveaux champs config `coma_mortel` (true), `defib_battements` (12), `defib_precision_min` (0.7).

- [ ] **Step 1: Écrire `SecoursConfigTest`** : un JSON ancien `{"bandage":{"item":"minecraft:paper","secondes":4,"consomme":true,"reserve_secours":false}}` désérialisé par Gson dans `SecoursConfig` donne `bandage.secondes == 4` sans exception ; un JSON `{}` donne `defib_battements == 12`, `coma_mortel == true`, `defib_precision_min == 0.7`. Lancer : échec (champs absents).

- [ ] **Step 2: Modifier `SecoursConfig`** : retirer `item` d'`Objet` (constructeur à 3 paramètres), remplacer les tests `.item == null` de `load()` par `== null`, ajouter les 3 champs, mettre `coeurs_apres_reanimation = 20` (plafonné par la vie max), mettre à jour `_aide`. Relancer le test : PASS.

- [ ] **Step 3: Créer `CareItem extends Item`** : `super(new Item.Properties().stacksTo(stack))`, aucun `use` (les soins passent par les événements existants).

- [ ] **Step 4: `ModItems`** : enregistrer `bandage` (type `SecoursService.BANDAGE`, pile 16), `trousse_soins` (TROUSSE, 16), `defibrillateur` (DEFIB, pile 1). Ajouter les 4 items à `displayItems` de l'onglet.

- [ ] **Step 5: `SecoursService.careType(ItemStack)`** : retourne `ci.type` si `stack.getItem() instanceof CareItem ci`, sinon -1 ; supprimer les imports devenus inutiles.

- [ ] **Step 6: Écrire `tools/GenAssets.java`** (Java seul, `javax.imageio`) qui génère `bandage.png` et `trousse_soins.png` (16×16 : bandage blanc + croix rouge ; trousse boîte rouge + croix blanche) et `defibrillateur.png` (32×32 : gris, palettes jaune/rouge, écran vert) dans `src/main/resources/assets/minenorthsecours/textures/item/`. Exécuter : `java tools/GenAssets.java`.

- [ ] **Step 7: Modèles JSON** : `bandage.json` et `trousse_soins.json` en `item/generated` (`layer0`). `defibrillateur.json` : `textures.particle` et `0` = `minenorthsecours:item/defibrillateur`, `texture_size [32,32]`, `elements` : boîtier 10×6×4, poignée sur le dessus, écran en relief sur la face avant, deux palettes (2×4×1) ; `display` pour `thirdperson_righthand`, `firstperson_righthand`, `gui`, `ground`, `fixed`. Vérifier que le JSON est valide.

- [ ] **Step 8: Lang** : ajouter `item.minenorthsecours.bandage`, `item.minenorthsecours.trousse_soins`, `item.minenorthsecours.defibrillateur` en fr et en.

- [ ] **Step 9: Vérifier** `./gradlew build` — attendu : BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 10: Commit** `feat: items bandage, trousse, defibrillateur et onglet creatif`.

---

### Task 3: Soins complets

**Files:** Modify `SecoursService.java` (`wake`, `finishCare`)

**Interfaces:** Consumes `CareItem` (Task 2).

- [ ] **Step 1:** `wake(ServerPlayer, Injury)` : remplacer le plancher actuel par `p.setHealth(Math.max(p.getHealth(), (float) Math.min(p.getMaxHealth(), cfg.coeurs_apres_reanimation * 2.0)))` ; avec le défaut 20 cela rend toute la vie.
- [ ] **Step 2:** `finishCare`, branche trousse : ajouter `target.setHealth(target.getMaxHealth())`. Le bandage reste inchangé.
- [ ] **Step 3:** `./gradlew build` — attendu : BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `feat: reanimation et trousse rendent toute la vie`.

---

### Task 4: Coma mortel

**Files:** Modify `SecoursService.java` (`attacked`, `damage`, `death`, `hospital`, nouveau handler de respawn)

**Interfaces:** Produces `private static final Set<UUID> HOSPITAL_RESPAWN` ; `private static void hospitalArrival(ServerPlayer p, String note)` (téléportation au point d'hôpital si `hasHospital`, vie, facture, message), utilisée par `hospital()` et par le respawn.

- [ ] **Step 1:** `attacked` : n'annuler que si `!SecoursConfig.get().coma_mortel`. `damage` : `if (j.coma) { if (!cfg.coma_mortel) e.setCanceled(true); return; }`.
- [ ] **Step 2:** extraire de `hospital()` la téléportation + facture dans `hospitalArrival(p, note)` ; `hospital()` garde `injuries.remove`, `dismount`, puis appelle `hospitalArrival`.
- [ ] **Step 3:** `death(LivingDeathEvent)` : avant la suppression des blessures, si `peek(uuid)` est en coma et `coma_mortel` : `HOSPITAL_RESPAWN.add(uuid)`, `d.note(uuid, "Mort pendant le coma")`, `tellSecours` d'une alerte de décès ; nettoyer `CARRY` et `dismount`.
- [ ] **Step 4:** nouveau `@SubscribeEvent onRespawn(PlayerEvent.PlayerRespawnEvent)` : si `HOSPITAL_RESPAWN.remove(uuid)` et `!e.isEndConquered()` → `hospitalArrival(sp, "Réveil à l'hôpital après décès en coma")` ; sans point d'hôpital, simple message, sans exception. Retirer l'UUID dans le nettoyage à la déconnexion.
- [ ] **Step 5:** `./gradlew build`. Vérification manuelle : joueur en coma tué par un autre joueur → mort réelle → réapparition à l'hôpital avec facture ; `coma_mortel=false` → ancien comportement.
- [ ] **Step 6: Commit** `feat: coma mortel, reveil a l'hopital`.

---

### Task 5: Mini-jeu de défibrillation (serveur, réseau, écran)

**Files:**
- Modify: `network/ModNetwork.java`, `SecoursService.java`, `client/ClientNetworkHandler.java`
- Create: `client/DefibScreen.java`

**Interfaces:**
- Consumes : `DefibScore.beats(long,int)`, `DefibScore.evaluate(long[],long[],long)` (Task 1) ; `finishCare`, `careError` (existants).
- Produces :
  - `ModNetwork.DefibStartPacket(long seed, int beats)` S2C → `ClientNetworkHandler.defib(p)` ouvre `new DefibScreen(seed, beats)`.
  - `ModNetwork.DefibResultPacket(boolean cancelled, long[] taps)` C2S (liste limitée à 64 entrées) → `SecoursService.defibResult(ServerPlayer, DefibResultPacket)`.
  - `private record DefibSession(UUID target, long seed, int beats, long startMs)` et `Map<UUID, DefibSession> DEFIBS`.

- [ ] **Step 1: Réseau** : définir les deux paquets sur le modèle de `ComaListPacket`/`ActionPacket`, les enregistrer dans `register()` et passer `PROTOCOL` à `"3"`.
- [ ] **Step 2: `startCare`** : quand `type == DEFIB`, après les vérifications existantes, créer une `DefibSession` (graine `ThreadLocalRandom`, `beats = cfg.defib_battements`), l'ajouter à `DEFIBS`, envoyer `DefibStartPacket` au secouriste, prévenir le patient, retourner true sans créer de `Care`.
- [ ] **Step 3: `defibResult`** : retirer la session de l'émetteur ; si `cancelled` ou session absente, fin. Sinon `acc = DefibScore.evaluate(DefibScore.beats(seed, beats), taps, now - startMs)` ; si `acc >= cfg.defib_precision_min` et que distance, coma du patient et défibrillateur en main sont toujours valides → `finishCare(rescuer, target, DEFIB)` ; sinon `bar(rescuer, "§cChoc raté (précision X %). Recommencez.")` sans consommer l'item.
- [ ] **Step 4: Nettoyage** : `tickDefibs`, appelé dans la boucle d'une seconde, supprime les sessions dont le secouriste ou le patient est absent, trop loin, plus en coma, ou ouvertes depuis plus de 60 s ; ajouter la suppression des sessions concernées au nettoyage à la déconnexion.
- [ ] **Step 5: `DefibScreen`** (`Screen`) : horloge `System.currentTimeMillis()` depuis l'ouverture ; fond sombre, ligne de frappe fixe à droite, marqueurs-cœurs qui défilent vers elle (position selon `beatMs - elapsed`), tracé ECG animé, jauge de précision, jugement « PARFAIT / BON / RATÉ » à chaque frappe ; frappe sur clic gauche ou Espace (ajoute `elapsed` aux frappes) ; fin 800 ms après le dernier battement → envoie `DefibResultPacket(false, taps)` et ferme ; Échap → `DefibResultPacket(true, new long[0])`. `isPauseScreen()` false. Couleurs via `MineNorthStyle` si adapté.
- [ ] **Step 6: Vérification** : `./gradlew build` ; test manuel en jeu : réussir (>= 70 %) → réanimation, facture, item consommé selon `consomme` ; rater volontairement → message d'échec, item toujours en main ; Échap → rien ne se passe.
- [ ] **Step 7: Commit** `feat: mini-jeu de rythme pour le defibrillateur`.

---

### Task 6: README et vérification finale

**Files:** Modify `README.md`

- [ ] **Step 1:** README : remplacer la section « Objets de soin » (items du mod, plus d'id vanilla configurable), documenter le mini-jeu (12 battements, `defib_precision_min`), `coma_mortel` et le réveil à l'hôpital, la vie complète rendue.
- [ ] **Step 2:** `./gradlew clean build` — attendu : BUILD SUCCESSFUL, tests PASS.
- [ ] **Step 3: Commit** `docs: README items, mini-jeu, coma mortel`.
