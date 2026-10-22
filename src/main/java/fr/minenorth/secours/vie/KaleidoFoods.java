package fr.minenorth.secours.vie;

import java.util.ArrayList;
import java.util.List;

/**
 * Nourriture et boissons de la famille Kaleidoscope (Cookery 1.6.0, Tavern 1.2.0, World Liquor 1.1.12), classées pour l'équilibre alimentaire.
 * Identifiants relevés dans les jars du modpack (modèles d'objets) ; une liste est ajoutée à celle de la config si « ajoutCuisineKaleidoscope » = true.
 */
final class KaleidoFoods {
    private KaleidoFoods() {}

    private static List<String> ids(String ns, String names) {
        List<String> out = new ArrayList<>();
        for (String n : names.trim().split("\\s+")) if (!n.isEmpty()) out.add(ns + ":" + n);
        return List.copyOf(out);
    }

    private static List<String> join(List<String> a, List<String> b, List<String> c) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        out.addAll(c);
        return List.copyOf(out);
    }

    private static final String C = "kaleidoscope_cookery", T = "kaleidoscope_tavern", L = "kaleidoscope_world_liquor";

    /** Légumes, fruits, soupes, riz, thés et plats légers. */
    static final List<String> HEALTHY = join(
            ids(C, """
                    tomato green_chili red_chili lettuce rice cooked_rice apple_platter berry_platter tomato_platter watermelon_platter golden_salad
                    scramble_egg_with_tomatoes scramble_egg_with_tomatoes_rice_bowl egg_fried_rice fried_egg tea_egg pork_bone_soup seafood_miso_soup
                    lamb_and_radish_soup wild_mushroom_rabbit_soup borscht beef_meatball_soup four_joy_meatball_soup chicken_and_mushroom_stew laba_congee
                    brown_mushroom_pot_soup red_mushroom_pot_soup bamboo_tube_rice udon_noodle mantou baozi dumpling zongzi qingtuan
                    barley_tea flower_tea biluochun oolong tieguanyin sakura_fubuki sashimi"""),
            ids(T, "grape ice_grape gold_grape green_grape watermelon_juice"),
            ids(L, "cool_tea ice_tea shrimp_cocktail"));

    /** Viandes grasses, fritures, plats en sauce, pâtisseries et sucreries. */
    static final List<String> JUNK = join(
            ids(C, """
                    braised_beef braised_beef_rice_bowl braised_beef_with_potatoes braised_pork_ribs dongpo_pork stir_fried_pork_with_peppers
                    stir_fried_pork_with_peppers_rice_bowl sweet_and_sour_pork sweet_and_sour_pork_rice_bowl fish_flavored_shredded_pork
                    fish_flavored_shredded_pork_rice_bowl donkey_burger fried_spring_roll meat_pie samsa shengjian_mantou sticky_candy sticky_rice_cake
                    candied_potato fondant_pie stargazy_pie numbing_spicy_chicken spicy_chicken spicy_rabbit_head spicy_blood_stew hot_dry_noodles
                    beef_noodle hui_noodle cooked_lamb_chops cooked_pork_belly cooked_meatball cooked_cow_offal cooked_cut_small_meats blaze_lamb_chop
                    crystal_lamb_chop frost_lamb_chop pan_seared_knight_steak oil_splashed_fish cold_roasted_meat cold_cut_ham_slices
                    buddha_jumps_over_the_wall fried_caterpillar butter_tea clay_pot_milk_tea chorus_fried_egg stuffed_tiger_skin_pepper
                    dough_drop_soup pufferfish_soup fearsome_thick_soup stuffed_dough_food"""),
            ids(T, ""),
            ids(L, "cola pina_colada pine_colada kwas_chlebowy sour_plum jerk"));

    /** Cru, étrange ou louche : peut donner une intoxication. */
    static final List<String> RISKY = join(
            ids(C, """
                    raw_lamb_chops raw_cow_offal raw_pork_belly raw_cut_small_meats raw_meatball cold_style_sashimi desert_style_sashimi
                    end_style_sashimi nether_style_sashimi tundra_style_sashimi caterpillar dark_cuisine suspicious_stir_fry slime_ball_meal
                    fondant_spider_eye mystery_tea sweet_and_sour_ender_pearls"""),
            ids(T, ""),
            ids(L, ""));

    /** Alcools et cocktails : augmentent les toxines. */
    static final List<String> TOXIC = join(
            ids(C, ""),
            ids(T, """
                    bloody_mary brandy carignan champagne depth_charge glowflower_brew godfather grasshopper honey_wine ice_wine luminous_bride
                    madame_shexiang miners_star mojito mother_snow mystery_cocktail nether_special plum_wine polaris_sweet_white red_queen
                    riesling_dry_white rum sakura_wine sauvignon_blanc_dry_white screwdriver sculk_special sherry signature_cocktail sunset_glow
                    sweet_berry_wine vodka whiskey white_lady wine"""),
            ids(L, """
                    absolut_vodka around_the_world bacardi_carta_blanca bamboo_leaf_green_liquor bombay_sapphire_gin cosmopolitan dassai gin_tonic
                    highball jack_daniel johnnie_walker lafite_1982 long_island_iced_tea maotai skyy_vodka smirnoff_red_vodka
                    spiryt_vodka strongbow"""));
}
