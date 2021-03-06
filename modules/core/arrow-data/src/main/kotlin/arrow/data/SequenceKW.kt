package arrow.data

import arrow.*
import arrow.core.Either
import arrow.core.Eval
import arrow.core.Tuple2
import arrow.typeclasses.Applicative

fun <A> SequenceKOf<A>.toList(): List<A> = this.extract().sequence.toList()

@higherkind
data class SequenceK<out A> constructor(val sequence: Sequence<A>) : SequenceKOf<A>, Sequence<A> by sequence {

    fun <B> flatMap(f: (A) -> SequenceKOf<B>): SequenceK<B> = this.extract().sequence.flatMap { f(it).extract().sequence }.k()

    fun <B> ap(ff: SequenceKOf<(A) -> B>): SequenceK<B> = ff.extract().flatMap { f -> map(f) }.extract()

    fun <B> map(f: (A) -> B): SequenceK<B> = this.extract().sequence.map(f).k()

    fun <B> foldLeft(b: B, f: (B, A) -> B): B = this.extract().fold(b, f)

    fun <B> foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> {
        fun loop(fa_p: SequenceK<A>): Eval<B> = when {
            fa_p.sequence.none() -> lb
            else -> f(fa_p.first(), Eval.defer { loop(fa_p.drop(1).k()) })
        }
        return Eval.defer { loop(this.extract()) }
    }

    fun <G, B> traverse(f: (A) -> Kind<G, B>, GA: Applicative<G>): Kind<G, SequenceK<B>> =
            foldRight(Eval.always { GA.pure(emptySequence<B>().k()) }) { a, eval ->
                GA.map2Eval(f(a), eval) { (sequenceOf(it.a) + it.b).k() }
            }.value()

    fun <B, Z> map2(fb: SequenceKOf<B>, f: (Tuple2<A, B>) -> Z): SequenceK<Z> =
            this.extract().flatMap { a ->
                fb.extract().map { b ->
                    f(Tuple2(a, b))
                }
            }.extract()

    companion object {

        fun <A> pure(a: A): SequenceK<A> = sequenceOf(a).k()

        fun <A> empty(): SequenceK<A> = emptySequence<A>().k()

        fun <A, B> tailRecM(a: A, f: (A) -> Kind<ForSequenceK, Either<A, B>>): SequenceK<B> {
            tailrec fun <A, B> go(
                    buf: MutableList<B>,
                    f: (A) -> Kind<ForSequenceK, Either<A, B>>,
                    v: SequenceK<Either<A, B>>) {
                if (!(v.toList().isEmpty())) {
                    val head: Either<A, B> = v.first()
                    when (head) {
                        is Either.Right<A, B> -> {
                            buf += head.b
                            go(buf, f, v.drop(1).k())
                        }
                        is Either.Left<A, B> -> {
                            if (v.count() == 1)
                                go(buf, f, (f(head.a).extract()).k())
                            else
                                go(buf, f, (f(head.a).extract() + v.drop(1)).k())
                        }
                    }
                }
            }

            val buf = mutableListOf<B>()
            go(buf, f, f(a).extract())
            return SequenceK(buf.asSequence())
        }

    }
}

fun <A> SequenceK<A>.combineK(y: SequenceKOf<A>): SequenceK<A> = (this.sequence + y.extract().sequence).k()

fun <A> Sequence<A>.k(): SequenceK<A> = SequenceK(this)
