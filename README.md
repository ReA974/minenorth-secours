# MineNorth Secours

Blessures, hémorragies, coma, pompiers / SAMU et hôpital. Forge 1.20.1. Dépend de `minenorth_eurobank`.
Optionnels (détectés automatiquement) : TACZ (blessures par balle), Identité (noms RP), Portes (portes Pompier).

## Blessures
| Blessure | Cause par défaut | Effet | Guérison |
|---|---|---|---|
| Légère | chute de 4 blocs ou plus | 5 % plus lent | seule, 3 min |
| Moyenne | chute de 8 blocs ou plus, ou balle | 20 % plus lent | seule, 12 min |
| Grave | chute qui laisse 4 cœurs ou moins | 45 % plus lent | secours ou PNJ |
| Inconscient | il resterait 2 cœurs ou moins | au sol, aucune action | réanimation, sinon hôpital après 10 min |

Une balle (arme TACZ) provoque une hémorragie : 1 demi-cœur perdu toutes les 8 s jusqu'au bandage.

## Objets de soin
Trois items du mod, dans l'onglet créatif « Secours MineNorth » (avec la tablette). Durées, consommation et réserve aux secours : clés `bandage`, `trousse`, `defibrillateur` de la config.
- Bandage : tout le monde. Sur soi : sneak + clic droit. Sur un autre : clic droit sur lui. Arrête l'hémorragie, ne rend pas de vie.
- Trousse de soins : secours en service uniquement. Soigne toutes les blessures et rend **toute la vie**.
- Défibrillateur : secours en service uniquement, clic droit sur un inconscient. Lance un **mini-jeu de rythme** : cliquer (ou Espace) sur chaque battement qui atteint la ligne.
  Réussite à partir de `defib_precision_min` (0,7) sur `defib_battements` (12) battements : le patient est réanimé avec **toute sa vie**. Échec : l'objet n'est pas consommé, on recommence. Échap abandonne.
  Le serveur génère les battements et recalcule la précision : le client ne peut pas se déclarer vainqueur.

## Coma mortel
Avec `coma_mortel` (true par défaut), un inconscient n'est plus invulnérable : coups, balles, feu ou chute peuvent le tuer. Il meurt réellement, puis réapparaît à l'hôpital (`/secours hopital`) avec la facture d'hôpital et une note dans son dossier médical. Les secours en service sont prévenus. `coma_mortel: false` rétablit l'ancien comportement.

## Commandes (OP / console)
- `/soins <joueur>` : menu du PNJ de soins.
- `/reanimer <joueur>` : réanime un joueur inconscient.
- `/retirerblessures <joueur>` : retire toutes ses blessures.
- `/secours grade <joueur> <chef|medecin|secouriste|aucun>`, `/secours tablette <joueur>`.
- `/secours hopital ajouter <nom>` | `supprimer <nom>` | `liste` : points de réveil de l'hôpital (votre position). Plusieurs points possibles : chaque réveil va au point le moins occupé. Le joueur reste dans un rayon de `hopital_rayon_blocs` (20) pendant `hopital_duree_minutes` (5).
- `/secours urgences ajouter <nom>` | `supprimer <nom>` | `liste` : points de réveil des urgences. Un joueur mort avec une hémorragie grave s'y réveille, immobile et invulnérable pendant `urgences_duree_minutes` (10), puis il est soigné. Sans point d'urgences, il va à l'hôpital.
- `/secours reload` : recharge `config/minenorth_secours.json`.

## Zone touchée par une balle (TACZ)
| Zone | Effet | Protégée par |
|---|---|---|
| Tête | inconscient immédiatement | casque |
| Torse | hémorragie 2 fois plus rapide | plastron |
| Bras | coups et gestes affaiblis | plastron |
| Jambe | 50 % plus lent, plus de saut | jambières |

Si la pièce d'armure qui couvre la zone est portée, la balle est arrêtée : pas d'hémorragie, seulement une blessure légère.
Réglages : `protection_active`, `protection_points_min`, `protection_objets`, `protection_seulement_liste`.

## Secouristes (en service)
Sur la tablette : **Prendre mon service**. Sans service : pas d'alertes, pas de soins réservés, pas de transport.
- Sneak + clic droit, main vide, sur un joueur : diagnostic.
- Clic droit, main vide, sur un joueur inconscient : le porter. S'accroupir : le poser.
- En portant un blessé, clic droit sur un véhicule : l'y installer. Sur un véhicule MTS du pack `minenorthpolicecar` (VSAV), le blessé est installé **sur le brancard** (siège `seat_brancard`) ; le brancard sort et rentre avec les portes arrière. Autre véhicule MTS : premier siège libre.
- Trousse et défibrillateur envoient une facture au patient (`facture_soins_euros`, `facture_reanimation_euros`).
- Onglet Dossiers : historique médical de chaque citoyen.

## Autres causes de blessure
Explosions et accidents de véhicule, feu et lave, noyade, gros coups : voir les clés `blessures_*` et `coup_*` de la config.

## Incendies de service (liés à la carte)
Quand des pompiers sont **en service**, un incendie se déclenche sur un des **sites** définis par les OP (pas de sites = pas d'incendie).
- Le feu apparaît sur la **carte** (mod `minenorth_map`, à jour côté serveur ET clients) des pompiers en service, repère « Incendie : <site> ».
- Tablette > ALERTES : **J'Y VAIS** lance le **guidage** (flèche + distance) vers l'incendie. Même chose vers un blessé inconscient (le repère suit le blessé s'il est déplacé).
- **Flammes d'incendie** (bloc `minenorthsecours:foyer`, depuis le 29/10) : elles ne vieillissent pas, ne s'éteignent jamais seules (pluie, sol en pierre), ne se propagent pas comme le feu vanilla et ne brûlent aucun bloc. Une flamme qui disparaît a donc forcément été éteinte. C'est ce qui corrige les anciens bugs (feux qui s'éteignaient seuls sur la pierre et comptaient comme éteints, feux éteints à l'extincteur qui se rallumaient, feu vanilla qui brûlait le décor). Une flamme orpheline (incendie annulé pendant que la zone n'était pas chargée) disparaît d'elle-même dans les 10 s qui suivent le chargement.
- **Éteindre** : extincteur ou canon à eau d'**Immersive Vehicles (MTS)** (les foyers sont dans le tag `minecraft:fire`, que MTS éteint), lance à incendie, couverture anti-feu, seau ou potion d'eau, ou à la main. Le suivi vérifie chaque seconde quelles flammes restent ; tous les foyers éteints = incendie maîtrisé (fin d'alerte, repère retiré de la carte, prime).
- **Durée** : un incendie démarre avec `incendie_foyers_min`-`incendie_foyers_max` foyers (16-28 depuis le 02/11, avant 8-14) et se propage toutes les `incendie_propagation_secondes` (45 s, 2 à 3 foyers, jusqu'à `incendie_rayon` + `incendie_propagation_rayon_bonus`) tant que `incendie_duree_min_minutes` (8) n'est pas écoulée. **Braises** : s'il est éteint avant, 1 ou 2 braises cachées restent (invisibles, elles fument ; la caméra thermique les montre). À noyer en moins de `incendie_braises_secondes` (45 s), sinon le feu repart à cet endroit (au plus `incendie_reprises_max` = 3 fois).
- **Fumée** : à moins de `incendie_fumee_rayon` (3) blocs d'une flamme, un joueur sans **masque ARI** perd `incendie_fumee_degats` (1 = un demi-cœur) toutes les 2 s. `0` = pas de dégâts.
- **Labo clandestin** : l'accident d'un labo du mod drogue (explosion + feu) devient une intervention à part entière (« Labo clandestin x/z ») : alerte sonore, repère sur la carte, fin d'alerte quand le feu est éteint.
- **Prime** : quand l'incendie est vraiment maîtrisé, chaque pompier en service resté au moins `incendie_prime_presence_secondes` (30 s) à moins de `incendie_prime_distance` (40 blocs) reçoit `incendie_prime_euros` (250 €) sur son compte (source banque `secours:prime-incendie`, prélevée sur le trésor). Rien si l'incendie est raté ou coupé par commande. `0` = pas de prime.
- **Mode calme** : si trop de blessés sont à prendre en charge (inconscients, qui saignent, ou blessure d'au moins `incendie_calme_niveau_min`), plus de nouvel incendie, plus de propagation ni de reprise, et les pompiers peuvent terminer ceux en cours. Seuil : `max(2, pompiers en service x incendie_blesses_par_pompier)` ; un pompier blessé compte double. Les pompiers en service sont prévenus à l'entrée et à la sortie du mode calme. `incendie_calme_actif: false` pour désactiver.
- Commandes OP : `/secours kit <joueur>` (lance, 2 rouleaux de tuyau, hache, halligan, caméra thermique, couverture, masque ARI), `/secours incendie ajouter <nom>` (à votre position), `supprimer <nom>`, `liste`, `declencher [nom]`, `eteindre`.
- Choisissez des sols non inflammables (dalles, route) : le feu vanilla peut se propager aux blocs inflammables voisins.
- Réglages (`config/minenorth_secours.json`) : `incendies_actifs`, `incendie_delai_secondes`, `incendie_intervalle_minutes`, `incendie_max_simultanes`, `incendie_rayon`, `incendie_duree_max_minutes`.

## Matériel des pompiers (modèles 3D, onglet créatif MineNorth)
- **Bouche d'incendie** (`bouche_incendie`, bloc à poser, pioche pour la reprendre). **Lance à incendie** (`lance_incendie`) : clic droit sur une bouche d'incendie ou sur un **véhicule MTS** pour la brancher, puis clic droit maintenu pour le jet (portée `lance_portee` = 14 blocs). Le jet éteint flammes, braises, feu vanilla et les personnes en feu. Il faut au moins un **rouleau de tuyau** (`rouleau_tuyau`) dans l'inventaire pour brancher : `lance_longueur_tuyau` (30) blocs par rouleau, 3 rouleaux au plus. **Tuyau visible** : tant que la lance branchée est en main, un tuyau 3D (tissu rouge, raccords laiton) relie la main du pompier à la sortie de la bouche ou au véhicule. Tous les joueurs le voient, il pend et traîne au sol (relief relevé sous chaque point) et se tend en approchant de sa longueur (avertissement au-delà de 85 %). Il s'arrache au-delà de sa longueur, et la lance se débranche à la mort, au changement de dimension ou si le point d'eau disparaît. Rendu après les entités, sans allocation par image (Embeddium / Oculus). `lance_vehicules` : mots que doit contenir le nom du véhicule MTS (ex. `["pompier", "fpt"]`), liste vide = tout véhicule. Réservée aux pompiers / SAMU.
- **Hache de pompier** (4 s) et **halligan** (pied-de-biche, 2,5 s) : clic droit sur une porte, une trappe ou un portillon fermé pour le **forcer** (rien n'est cassé, la porte s'ouvre). Réservé aux pompiers **en service**, à moins de `forcement_rayon` (24) blocs d'un incendie en cours ou d'un blessé inconscient (`forcement_hors_intervention: true` pour lever cette condition). Chaque forcement est noté dans le journal du serveur. Les portes de **minenorth-portes** passent par le même clic que le crochet de serrurier : le mod Portes demande au mod Secours s'il peut forcer. Une interaction que WorldGuard refuse n'arrive donc jamais jusqu'au forcement.
- **Caméra thermique** : tenue en main (une des deux mains) par un pompier, elle montre à travers les murs, dans un rayon de 48 blocs, les foyers, les braises cachées et les blessés inconscients, avec la température et la distance. Les points hors champ sont indiqués au bord de l'écran. Dessin en surimpression (HUD), compatible Embeddium / Oculus.
- **Couverture anti-feu** (12 utilisations, tout le monde) : clic droit sur des flammes pour en étouffer jusqu'à 3, sur une personne en feu (ou dans le vide si l'on brûle soi-même) pour l'éteindre.
- **Masque ARI** : se met sur la tête (clic droit ou emplacement du casque), protège de la fumée. Modèle 3D sur le visage (agrandi de 25 % et recalé le 02/11 : il couvre maintenant le visage du menton au front ; `tools/GenAssets.masque`) ; bords de l'écran assombris et témoin « ARI » quand on le porte.

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).

## Module Santé (ex-mod minenorth-vie, fusionné)

Le mod **minenorth-vie** est fusionné ici (code dans `fr.minenorth.secours.vie`) : forme, alimentation, repos, toxines, immunité, maladies (mode FUN par défaut), salle de sport (machines 3D + `GymSeat`), écran Santé (touche H, `/sante`), `/vie`, `VieApi`, `VieWipe`, config `minenorth_vie.toml`.

- Les identifiants restent `minenorthvie:*` (objets, blocs, entité `gym_seat`, réseau) : aucun remapping nécessaire, objets/machines/statistiques existants conservés. Les assets et données sont dans `assets|data/minenorthvie/`.
- Migration : remplacer `minenorth-vie-1.0.0.jar` par ce jar (ne jamais garder les deux).
- Objets de santé en vrais modèles 3D : `java tools/GenSante.java` régénère textures et modèles (médicament, vitamines, vaccin) ; `tools/GenAssets.java` ne s'occupe que du matériel de secours.

## Incendies plus grands (02/11)
`incendie_rayon` passe de 2 à **4** blocs, `incendie_foyers_min`/`max` de 8-14 à **16-28**, `incendie_propagation_rayon_bonus` de 2 à **3**, et chaque propagation allume 2 à 3 foyers (au lieu de 1 à 2). Un `minenorth_secours.json` existant resté sur les anciennes valeurs par défaut (2 / 8 / 14) passe tout seul aux nouvelles au chargement (`incendie_taille_version` = 2) ; des valeurs réglées à la main sont gardées.

## Applications « Secours » et « Santé » dans le téléphone MRPhone (02/11)
Comme Mairie et Mon gang : les icônes **Secours** (pompiers / SAMU) et **Santé** du téléphone patché ouvrent l'application **dans l'écran du téléphone** (`client/phone/PhoneSecoursScreen`, `PhoneSanteScreen`, sous-classes de `AbstractZPhoneScreen` via `PhoneAppBase`, compilées contre `libs/mrphone-0.9.4.jar` en `compileOnly`).
- Secours : onglets Alertes (blessés, incendies, appels du 18, « J'y vais » / « Guider », rechargées toutes les 5 s), Dossiers (dossier médical), Effectifs (chef), Service (prise de service, réponse au 18 sur le téléphone). Mêmes paquets que la tablette (`TabletPacket` / `ActionPacket`), aucun changement réseau ; fermeture = `A_CLOSE`.
- Santé : forme, alimentation, repos, toxines, immunité, maladie, bonus (rafraîchi toutes les 3 s).
- Ouverture : `PhoneAppClient.openFromPhone` / `PhoneSante.openFromPhone` → `phone.PhoneSecours.request()` / `requestSante()` sans fermer le téléphone ; la réponse du serveur ouvre l'application (`PhoneNavigationState.open`). `PhoneAppClient.phone()` est testé AVANT tout appel au paquet `client.phone`. Sans MRPhone : écrans d'avant. La tablette (objet) garde son écran.
- **Non testé en jeu** (mise en page, tailles de texte).

## Plus de tablette donnée automatiquement (02/11)
Au recrutement (`setGrade`), plus de tablette : message vers l'application Secours du téléphone. Le stuff du casier de service (mod Police, `ServiceManager.template`) ne donne plus non plus la tablette. `/secours tablette` et le panneau admin restent possibles ; la tablette fonctionne toujours.
