@file:Suppress("unused", "ClassName")

package org.batnet.ecc

import org.batnet.BITS_PER_SYMBOL

/* These are common CRC polynomials copied from Wikipedia: https://en.wikipedia.org/wiki/Cyclic_redundancy_check#Polynomial_representations_of_cyclic_redundancy_checks */

object CRC_3_GSM {
    const val DATA_BITS = 12
    const val CHECKSUM_SIZE = 3

    const val POLYNOMIAL_NORMAL = 0x3
}

object CRC_4_ITU {
    const val DATA_BITS = 17
    const val CHECKSUM_SIZE = 4

    const val POLYNOMIAL_NORMAL = 0x3
}

object CRC_5_EPC {
    const val DATA_BITS = 16
    const val CHECKSUM_SIZE = 5

    const val POLYNOMIAL_NORMAL = 0x09
}

object CRC_6_GSM {
    const val DATA_BITS = 24
    const val CHECKSUM_SIZE = 6

    const val POLYNOMIAL_NORMAL = 0x2F
}

/* Choose which CRC to use here */
//val CRC = CRC_6_GSM
val CRC = CRC_5_EPC

const val ECC_DATA_BITS = CRC.DATA_BITS
const val ECC_CHECKSUM_SIZE = CRC.CHECKSUM_SIZE
const val BLOCK_SIZE_WITH_ECC = (ECC_DATA_BITS + ECC_CHECKSUM_SIZE) / BITS_PER_SYMBOL
const val CRC_POLYNOMIAL = CRC.POLYNOMIAL_NORMAL or (1 shl CRC.CHECKSUM_SIZE)
