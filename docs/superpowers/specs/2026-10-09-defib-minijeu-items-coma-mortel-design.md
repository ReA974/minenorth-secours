# Défibrillateur mini-jeu, vrais items, coma mortel, soins complets

## Objectifs
1. Défibrillateur : item avec modèle 3D + mini-jeu de compressions (12 battements) à la place de la barre de chargement.
2. Un inconscient peut mourir (tout dégât) et se réveille à l'hôpital.
3. Trousse et réanimation rendent tous les cœurs.
4. Onglet créatif « Secours MineNorth » contenant tous les items du mod.

## Items (ModItems)
- Nouveaux items : `bandage`, `trousse_soins`, `defibrillateur` (+ tablette existante), tous dans l'onglet créatif.
- Modèle du défibrillateur : JSON `elements` (boîtier, 2 palettes, écran) + texture PNG générée. Bandage/trousse : modèle `generated` + texture.
- `SecoursService.careType()` compare l'instance d'item. Les champs `item` de `SecoursConfig.Objet` sont supprimés (ancien JSON : champ ignoré). `secondes`, `consomme`, `reserve_secours` restent.

## Mini-jeu (défibrillateur)
- Clic droit sur un inconscient : le serveur ouvre une session (`DEFIBS` : secouriste, cible, graine, nombre de battements = 12, `startMs`) et envoie `DefibStartPacket` ; le client ouvre `DefibScreen`.
- `DefibScreen` : tracé de battement qui défile, marqueurs vers une ligne de frappe, clic/Espace en rythme, jugement Parfait / Bon / Raté, jauge de rythme.
- Fin : `DefibResultPacket` avec les horodatages des frappes. Le serveur recalcule la précision à partir de la graine (les temps des battements viennent du serveur), vérifie durée minimale et nombre de frappes plausibles.
- Précision >= `defib_precision_min` (config, 0.7) : `finishCare` (réanimation). Sinon échec, défibrillateur non consommé, on peut recommencer.
- Annulation : fermeture de l'écran, distance > `distance_soin`, patient réveillé/déconnecté, secouriste inconscient ou hors service.
- Bandage et trousse gardent la barre de progression actuelle (`Care`).

## Coma mortel
- Config `coma_mortel` (true par défaut). Si true, `attacked` et `damage` ne bloquent plus les dégâts sur un inconscient ; s'applique aussi aux joueurs exempts.
- Mort d'un inconscient (`LivingDeathEvent`) : état de coma effacé, note dans le dossier médical, alerte aux secours ; au respawn, réveil au point `/secours hopital` avec la facture d'hôpital (logique de `hospital()` réutilisée).
- `coma_mortel=false` : ancien comportement.

## Soins complets
- `wake()` (réanimation) et la trousse font `setHealth(getMaxHealth())`. `coeurs_apres_reanimation` reste en config, défaut = vie max. Le bandage ne rend pas de vie.

## Tests
- `./gradlew build`.
- Logique de validation du score isolée en classe pure (`DefibScore`) avec tests unitaires JUnit.
- Rendu du modèle vérifié par lecture du JSON.

## Hors périmètre
Pas de nouvelles recettes de craft ni de son personnalisé.
