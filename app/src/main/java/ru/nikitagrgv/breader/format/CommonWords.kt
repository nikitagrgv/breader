package ru.nikitagrgv.breader.format

/**
 * The words of a language that every text is full of: articles, pronouns, prepositions, conjunctions, auxiliary verbs
 * and the like. Whoever reads a book in the language knows them, so they can be left out of its words to learn.
 * In lowercase, as [WordCount.key]s.
 */
enum class CommonWords(list: String) {
    ENGLISH(
        """
        a an the
        i me my mine myself you your yours yourself yourselves he him his himself she her hers herself it its itself
        we us our ours ourselves they them their theirs themselves
        this that these those who whom whose which what
        am is are was were be been being have has had having do does did doing
        will would shall should can could may might must
        of to in on at by for with from into onto upon about above below over under after before between through
        during without within against among around across along behind beside beyond toward towards off out up down
        near since until till
        and or but nor so yet if than then because as while though although unless whether both either neither
        some any no not all each every few more most other such own same only very too also just
        there here when where why how again once
        don't doesn't didn't isn't aren't wasn't weren't haven't hasn't hadn't won't wouldn't can't couldn't
        shouldn't mustn't i'm i've i'll i'd you're you've you'll you'd he's he'll he'd she's she'll she'd it's
        we're we've we'll we'd they're they've they'll they'd that's there's here's what's who's let's
        """,
    ),
    RUSSIAN(
        """
        я меня мне мной мною ты тебя тебе тобой тобою он его ему им нём нем него нему ним она её ее ей ней неё нее
        нею ею оно мы нас нам нами вы вас вам вами они их ими них ними себя себе собой собою
        мой моя моё мое мои моего моей моему моих мою моим моими твой твоя твоё твое твои твоего твоей твоему твоих
        твою твоим свой своя своё свое свои своего своей своему своих свою своим своими наш наша наше наши нашего
        нашей нашему наших нашу нашим ваш ваша ваше ваши вашего вашей вашему ваших вашу вашим
        этот эта это эти этого этой этому этом эту этих этим этими тот та то те того той тому том ту тех тем теми
        весь вся всё все всего всей всему всем всех всю всеми сам сама само сами самого самой самому самим
        который которая которое которые которого которой которому котором которую которых которым которыми
        кто кого кому кем ком что чего чему чем чём такой такая такое такие такого такой какой какая какое какие
        в во на с со к ко по за из изо от ото до о об обо у для без под подо над перед при про через между около
        после среди из-за из-под вокруг
        и а но или да что чтобы чтоб если как когда потому хотя ни ли же ж бы б то не нет вот вон ну уж уже
        ещё еще даже только тоже также ведь
        быть был была было были будет будут буду будешь будем будете есть
        там тут здесь где куда туда сюда так тогда теперь потом
        """,
    ),
    GERMAN(
        """
        der die das des dem den ein eine einer eines einem einen
        ich du er sie es wir ihr mich mir dich dir ihn ihm uns euch ihnen sich
        mein meine meinen meinem meiner meines dein deine deinen deinem deiner deines sein seine seinen seinem
        seiner seines ihre ihren ihrem ihrer ihres unser unsere unseren unserem unserer euer eure euren eurem eurer
        dies diese dieser dieses diesem diesen jede jeder jedes jedem jeden alle allen aller alles man
        wer wen wem wessen was welche welcher welches
        in im an am auf aus bei beim mit nach von vom zu zum zur für über unter vor hinter neben zwischen durch
        gegen ohne um bis seit ins ans aufs
        und oder aber doch denn sondern dass ob weil wenn als wie da dann so auch noch schon nur nicht kein keine
        keinen keinem keiner ja nein sehr mehr hier dort wo
        bin bist ist sind seid war warst waren wart gewesen hat habe hast haben habt hatte hatten gehabt
        wird werde wirst werden wurde wurden würde worden kann können konnte muss müssen musste soll sollen sollte
        will wollen wollte
        """,
    ),
    FRENCH(
        """
        le la les un une des du de et ou mais donc or ni car
        je tu il elle on nous vous ils elles me te se lui leur leurs eux moi toi soi
        mon ma mes ton ta tes son sa ses notre nos votre vos
        ce cet cette ces cela ça ceci celui celle ceux celles qui que quoi dont où
        à au aux en dans par pour sur sous avec sans chez vers entre contre depuis pendant avant après
        ne pas plus non oui si comme quand aussi très tout tous toute toutes même y
        est sont était étaient été être suis es sommes êtes sera a ai as avons avez ont avait avaient avoir eu
        c'est c'était j'ai n'est n'a n'était qu'il qu'elle qu'on d'un d'une l'on s'il
        """,
    ),
    SPANISH(
        """
        el la los las lo un una unos unas de del a al en y e o u que no se por con para
        yo tú él ella ello ellos ellas nosotros nosotras vosotros usted ustedes me te le les nos os
        mi mis tu tus su sus nuestro nuestra nuestros nuestras
        este esta esto estos estas ese esa eso esos esas aquel aquella aquello
        qué quién cuál cuando donde como cómo más pero sin sobre entre hasta desde muy ya también porque si sí ni
        todo toda todos todas otro otra mismo
        es son era eran fue fueron ser soy eres estar está están estaba ha han había he has hemos hay
        """,
    ),
    ITALIAN(
        """
        il lo la i gli le un uno una
        di a da in con su per tra fra del dello della dei degli delle al allo alla ai agli alle dal dallo dalla
        dai dagli dalle nel nello nella nei negli nelle sul sullo sulla sui sugli sulle
        e ed o ma che non se come anche più
        io tu lui lei noi voi loro mi ti ci vi si ne me te
        mio mia miei mie tuo tua tuoi tue suo sua suoi sue nostro nostra vostro vostra
        questo questa questi queste quello quella quelli quelle chi cosa quando dove perché
        è sono era erano essere ho hai ha abbiamo avete hanno avere aveva stato c'è
        molto tutto tutti già poi così ancora solo
        """,
    );

    val words: Set<String> = list.trim().split(Regex("\\s+")).toSet()

    companion object {
        /**
         * The common words of the language a text with the [frequency] is in, i.e. the ones that make up the most of
         * it; null if none makes up much: then it's in a language whose common words aren't known.
         */
        fun of(frequency: WordFrequency): CommonWords? =
            entries.map { it to frequency.share(it.words) }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= MIN_SHARE }
                ?.first

        /** They make up a third of a text or more; the ones of another language, a few percent. */
        private const val MIN_SHARE = 0.15
    }
}
