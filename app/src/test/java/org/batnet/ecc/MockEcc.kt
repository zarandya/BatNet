package org.batnet.ecc

import io.mockk.every
import io.mockk.mockkStatic
import org.batnet.b
import org.batnet.utils.oneComplex

fun disableEccCarrierPhaseDriftCorrection() {
    mockkStatic("org.batnet.ecc.ErrorCorrectingCodeKt")

    every { getMostLikelySequence(any(), any()) } answers {
        (arg(1) as List<Int>).fold<Int, SymbolAndScoreListLink?>(null) { link, element ->
            SymbolAndScoreListLink(link, element.b, oneComplex)
        }!!
    }
}
