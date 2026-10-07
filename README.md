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

## Objets de soin (modifiables dans la config)
- Bandage : `minecraft:paper` — tout le monde. Sur soi : sneak + clic droit. Sur un autre : clic droit sur lui.
- Trousse de soins : `minecraft:glistering_melon_slice` — secours uniquement, soigne toutes les blessures.
- Défibrillateur : `minecraft:totem_of_undying` — secours uniquement, réanime.

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
- En portant un blessé, clic droit sur un véhicule : l'y installer.
- Trousse et défibrillateur envoient une facture au patient (`facture_soins_euros`, `facture_reanimation_euros`).
- Onglet Dossiers : historique médical de chaque citoyen.

## Autres causes de blessure
Explosions et accidents de véhicule, feu et lave, noyade, gros coups : voir les clés `blessures_*` et `coup_*` de la config.
