package com.example.data.util

import kotlin.random.Random

object Ya2neClashCommentator {

    val CATCHPHRASES = listOf(
        "TCHIEEEEY !",
        "OH TCHIEEEEY !",
        "AÏE AÏE AÏE TCHIEEEEY !",
        "TCHIEEEEY MON DIEU !"
    )

    private val HUMILIATING_PUNCHLINES = listOf(
        "TCHIEEEEY ! Oh le cassage de reins illégal ! Envoyez-lui un Uber pour rentrer chez lui immédiatement !",
        "AÏE AÏE AÏE TCHIEEEEY ! Le petit pont d'une insolence rare ! Il lui a éteint la carrière sur place !",
        "TCHIEEEEY ! Il a envoyé le défenseur acheter du pain et des croissants ! Appelez le SAMU, il cherche encore ses appuis !",
        "OH TCHIEEEEY ! Mais quel manque de respect légendaire ! Ses chevilles sont restées au rond central !",
        "TCHIEEEEY ! Double contact dévastateur ! Le défenseur a demandé la sortie sur civière tellement c'est humiliant !",
        "TCHIEEEEY ! Rouleau compresseur ! Il l'a envoyé dans les tribunes avec une simple feinte de corps !",
        "AÏE TCHIEEEEY ! Petit pont dévastateur avec le grand pont dans la foulée ! Respecte sa famille quand même !",
        "TCHIEEEEY ! Il lui a fait la totale ! Le défenseur a glissé jusqu'à la buvette du stade !"
    )

    // TTS text optimized for high energy / punchy French speech synthesis
    private val SPOKEN_TTS_LINES = listOf(
        "Tchieeey ! Oh l'irrespect total ! Il lui a brisé les reins ! Envoyez un Uber !",
        "Aïe aïe aïe Tchieeey ! Le petit pont insolent ! Il lui a éteint la carrière direct !",
        "Tchieeey ! Il l'a envoyé acheter du pain ! Appelez les secours immédiatement !",
        "Oh Tchieeey ! Mais quel cassage de chevilles ! Ses appuis sont restés au vestiaire !",
        "Tchieeey ! Feinte de frappe dévastatrice ! Le défenseur est parti glisser jusqu'au parking !"
    )

    fun getRandomClashReaction(playerName: String? = null): Pair<String, String> {
        val player = playerName ?: "L'attaquant"
        val textIndex = Random.nextInt(HUMILIATING_PUNCHLINES.size)
        val ttsIndex = Random.nextInt(SPOKEN_TTS_LINES.size)
        val text = "🔥 $player • " + HUMILIATING_PUNCHLINES[textIndex]
        val spoken = SPOKEN_TTS_LINES[ttsIndex]
        return text to spoken
    }
}
