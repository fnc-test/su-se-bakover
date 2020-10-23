package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import no.nav.su.se.bakover.domain.brev.Satsgrunn

data class Behandlingsinformasjon(
    val uførhet: Uførhet? = null,
    val flyktning: Flyktning? = null,
    val lovligOpphold: LovligOpphold? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val oppholdIUtlandet: OppholdIUtlandet? = null,
    val formue: Formue? = null,
    val personligOppmøte: PersonligOppmøte? = null,
    val bosituasjon: Bosituasjon? = null
) {
    fun patch(
        b: Behandlingsinformasjon
    ) = Behandlingsinformasjon(
        uførhet = b.uførhet ?: this.uførhet,
        flyktning = b.flyktning ?: this.flyktning,
        lovligOpphold = b.lovligOpphold ?: this.lovligOpphold,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        oppholdIUtlandet = b.oppholdIUtlandet ?: this.oppholdIUtlandet,
        formue = b.formue ?: this.formue,
        personligOppmøte = b.personligOppmøte ?: this.personligOppmøte,
        bosituasjon = b.bosituasjon ?: this.bosituasjon
    )

    private fun isComplete() =
        listOf(
            uførhet,
            flyktning,
            lovligOpphold,
            fastOppholdINorge,
            oppholdIUtlandet,
            formue,
            personligOppmøte,
            bosituasjon
        ).all { it != null && it.isValid() && it.isComplete() }

    fun isInnvilget() =
        isComplete() &&
            listOf(
                uførhet?.status == Uførhet.Status.VilkårOppfylt,
                flyktning?.status == Flyktning.Status.VilkårOppfylt,
                lovligOpphold?.status == LovligOpphold.Status.VilkårOppfylt,
                fastOppholdINorge?.status == FastOppholdINorge.Status.VilkårOppfylt,
                oppholdIUtlandet?.status == OppholdIUtlandet.Status.SkalHoldeSegINorge,
                formue?.status == Formue.Status.VilkårOppfylt,
                personligOppmøte?.status.let {
                    it == PersonligOppmøte.Status.FullmektigMedLegeattest ||
                        it == PersonligOppmøte.Status.MøttPersonlig ||
                        it == PersonligOppmøte.Status.Verge
                }
            ).all { it }

    fun isAvslag() =
        listOf(
            uførhet?.status == Uførhet.Status.VilkårIkkeOppfylt,
            flyktning?.status == Flyktning.Status.VilkårIkkeOppfylt,
            lovligOpphold?.status == LovligOpphold.Status.VilkårIkkeOppfylt,
            formue?.status == Formue.Status.VilkårIkkeOppfylt,
            fastOppholdINorge?.status == FastOppholdINorge.Status.VilkårIkkeOppfylt,
            oppholdIUtlandet?.status == OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            personligOppmøte?.status.let {
                it == PersonligOppmøte.Status.FullmektigUtenLegeattest ||
                    it == PersonligOppmøte.Status.IkkeMøttOpp
            }
        ).any { it }

    fun getAvslagsgrunn() = when {
        uførhet?.status == Uførhet.Status.VilkårIkkeOppfylt -> Avslagsgrunn.UFØRHET
        flyktning?.status == Flyktning.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FLYKTNING
        lovligOpphold?.status == LovligOpphold.Status.VilkårIkkeOppfylt -> Avslagsgrunn.OPPHOLDSTILLATELSE
        fastOppholdINorge?.status == FastOppholdINorge.Status.VilkårIkkeOppfylt -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
        oppholdIUtlandet?.status == OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet -> Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
        formue?.status == Formue.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FORMUE
        personligOppmøte?.status.let { s ->
            s == PersonligOppmøte.Status.IkkeMøttOpp ||
                s == PersonligOppmøte.Status.FullmektigUtenLegeattest
        } -> Avslagsgrunn.PERSONLIG_OPPMØTE
        else -> null
    }

    abstract class Base {
        abstract fun isValid(): Boolean
        abstract fun isComplete(): Boolean
    }

    data class Uførhet(
        val status: Status,
        val uføregrad: Int?,
        val forventetInntekt: Int?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            HarUføresakTilBehandling
        }

        override fun isValid(): Boolean =
            when (status) {
                Status.VilkårOppfylt -> uføregrad != null && forventetInntekt != null
                Status.VilkårIkkeOppfylt -> uføregrad == null && forventetInntekt == null
                Status.HarUføresakTilBehandling -> uføregrad != null && uføregrad > 0 && forventetInntekt != null && forventetInntekt > 0
            }

        override fun isComplete(): Boolean = isValid()
    }

    data class Flyktning(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = status != Status.Uavklart
    }

    data class LovligOpphold(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = status != Status.Uavklart
    }

    data class FastOppholdINorge(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = status != Status.Uavklart
    }

    data class OppholdIUtlandet(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            SkalVæreMerEnn90DagerIUtlandet,
            SkalHoldeSegINorge
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = true
    }

    data class Formue(
        val status: Status,
        val verdiIkkePrimærbolig: Int?,
        val verdiKjøretøy: Int?,
        val innskudd: Int?,
        val verdipapir: Int?,
        val pengerSkyldt: Int?,
        val kontanter: Int?,
        val depositumskonto: Int?,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            MåInnhenteMerInformasjon
        }

        override fun isValid(): Boolean =
            when (status) {
                Status.MåInnhenteMerInformasjon -> true
                else ->
                    verdiIkkePrimærbolig !== null &&
                        verdiKjøretøy !== null &&
                        innskudd !== null &&
                        verdipapir !== null &&
                        pengerSkyldt !== null &&
                        kontanter !== null &&
                        depositumskonto !== null
            }

        override fun isComplete(): Boolean = status != Status.MåInnhenteMerInformasjon
    }

    data class PersonligOppmøte(
        val status: Status,
        val begrunnelse: String?
    ) : Base() {
        enum class Status {
            MøttPersonlig,
            Verge,
            FullmektigMedLegeattest,
            FullmektigUtenLegeattest,
            IkkeMøttOpp
        }

        override fun isValid(): Boolean = true
        override fun isComplete(): Boolean = true
    }

    data class Bosituasjon(
        val delerBolig: Boolean,
        val delerBoligMed: Boforhold.DelerBoligMed?,
        val ektemakeEllerSamboerUnder67År: Boolean?,
        val ektemakeEllerSamboerUførFlyktning: Boolean?,
        val begrunnelse: String?
    ) : Base() {
        fun utledSats() =
            if (!delerBolig) {
                Sats.HØY
            } else {
                // Vi gjør en del null assertions her for at logikken ikke skal bli så vanskelig å følge
                // Det _bør_ være trygt fordi gyldighet av objektet skal bli sjekket andre plasser
                when (delerBoligMed!!) {
                    Boforhold.DelerBoligMed.VOKSNE_BARN ->
                        Sats.ORDINÆR
                    Boforhold.DelerBoligMed.ANNEN_VOKSEN ->
                        Sats.ORDINÆR
                    Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER ->
                        if (!ektemakeEllerSamboerUnder67År!!) {
                            Sats.ORDINÆR
                        } else {
                            if (ektemakeEllerSamboerUførFlyktning!!) {
                                Sats.ORDINÆR
                            } else {
                                Sats.HØY
                            }
                        }
                }
            }

        override fun isValid(): Boolean =
            if (delerBolig && delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER) {
                if (ektemakeEllerSamboerUnder67År == true) {
                    ektemakeEllerSamboerUførFlyktning != null
                } else {
                    ektemakeEllerSamboerUførFlyktning == null
                }
            } else {
                true
            }

        fun getSatsgrunn() = when {
            !delerBolig -> Satsgrunn.ENSLIG
            delerBoligMed == Boforhold.DelerBoligMed.VOKSNE_BARN ->
                Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            delerBoligMed == Boforhold.DelerBoligMed.ANNEN_VOKSEN ->
                Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektemakeEllerSamboerUnder67År == false ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
            delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektemakeEllerSamboerUnder67År == true && ektemakeEllerSamboerUførFlyktning == false ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
            delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektemakeEllerSamboerUførFlyktning == true ->
                Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
            else -> null
        }

        override fun isComplete(): Boolean = true
    }

    companion object {
        fun lagTomBehandlingsinformasjon() = Behandlingsinformasjon(
            uførhet = null,
            flyktning = null,
            lovligOpphold = null,
            fastOppholdINorge = null,
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null
        )
    }
}
