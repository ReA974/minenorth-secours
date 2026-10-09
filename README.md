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
- `/secours hopital` : définit le point de réveil à l'hôpital (votre position).
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
- Éteindre : casser les flammes à la main ou verser de l'eau. Tous les foyers éteints = incendie maîtrisé. Les foyers qui s'éteignent seuls se rallument.
- Au bout de `incendie_duree_max_minutes` (30) le feu est coupé d'office ; s'il n'y a plus aucun pompier en service pendant 90 s, tout est éteint.
- Commandes OP : `/secours incendie ajouter <nom>` (à votre position), `supprimer <nom>`, `liste`, `declencher [nom]`, `eteindre`.
- Choisissez des sols non inflammables (dalles, route) : le feu vanilla peut se propager aux blocs inflammables voisins.
- Réglages (`config/minenorth_secours.json`) : `incendies_actifs`, `incendie_delai_secondes`, `incendie_intervalle_minutes`, `incendie_max_simultanes`, `incendie_rayon`, `incendie_duree_max_minutes`.

## Licence

**Tous droits réservés - MineNorthRP.** Réutilisation, copie, modification, décompilation / ingénierie
inverse (y compris par outils d'intelligence artificielle) et utilisation pour entraîner une IA sont
**interdites** sans autorisation écrite. Voir [LICENSE](LICENSE).
