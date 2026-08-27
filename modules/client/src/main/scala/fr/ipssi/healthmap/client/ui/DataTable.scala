package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

/** Tableau triable générique.
  *
  * Le Python affichait des `st.dataframe` déjà triés côté serveur, sans
  * interaction possible. Ici le tri est local : un clic sur un en-tête trie
  * selon l'`Ordering` de la colonne, un second clic inverse le sens. Les données
  * restent un `Signal`, donc le tableau suit le filtre profession sans être
  * reconstruit.
  */
object DataTable:

  /** @param libelle  en-tête de colonne
    * @param valeur   cellule, déjà mise en forme
    * @param tri      ordre appliqué au clic sur l'en-tête ; `None` rend la colonne non triable
    * @param numerique aligne la colonne à droite et rend le chiffre tabulaire
    */
  final case class Column[A](
      libelle: String,
      valeur: A => String,
      tri: Option[Ordering[A]] = None,
      numerique: Boolean = false
  )

  /** Colonne numérique triable, construite depuis un extracteur entier. */
  def nombre[A](libelle: String, extrait: A => Int): Column[A] =
    Column(libelle, a => Format.entier(extrait(a)), Some(Ordering.by(extrait)), numerique = true)

  /** Colonne texte triable. */
  def texte[A](libelle: String, extrait: A => String): Column[A] =
    Column(libelle, extrait, Some(Ordering.by(extrait)))

  /** @param rang affiche une première colonne de numérotation, à partir de 1
    * @param vide message affiché quand la liste est vide
    */
  def apply[A](
      lignes: Signal[List[A]],
      colonnes: List[Column[A]],
      rang: Boolean = false,
      vide: String = "Aucune donnée pour ce filtre."
  ): HtmlElement =

    // (index de colonne, ordre croissant). `None` conserve l'ordre du serveur.
    val etatTri = Var[Option[(Int, Boolean)]](None)

    val triees: Signal[List[A]] =
      lignes.combineWith(etatTri.signal).map {
        case (liste, None) => liste
        case (liste, Some((index, croissant))) =>
          colonnes.lift(index).flatMap(_.tri) match
            case None      => liste
            case Some(ord) => if croissant then liste.sortWith(ord.lt) else liste.sortWith(ord.gt)
      }

    div(
      cls := "hm-tablewrap",
      table(
        cls := "hm-table",
        thead(
          tr(
            if rang then List(th(cls := "hm-rang", "Rang")) else Nil,
            colonnes.zipWithIndex.map { case (colonne, index) => enTete(colonne, index, etatTri) }
          )
        ),
        tbody(
          children <-- triees.map { liste =>
            if liste.isEmpty then
              List(tr(td(cls := "hm-vide", colSpan := colonnes.size + (if rang then 1 else 0), vide)))
            else
              liste.zipWithIndex.map { case (ligne, index) =>
                tr(
                  if rang then List(td(cls := "hm-rang", (index + 1).toString)) else Nil,
                  colonnes.map(colonne =>
                    td(cls.toggle("hm-num") := colonne.numerique, colonne.valeur(ligne))
                  )
                )
              }
          }
        )
      )
    )

  private def enTete[A](colonne: Column[A], index: Int, etatTri: Var[Option[(Int, Boolean)]]): HtmlElement =
    val triable = colonne.tri.isDefined
    th(
      cls.toggle("hm-num") := colonne.numerique,
      cls.toggle("hm-triable") := triable,
      onClick --> Observer[Any] { _ =>
        if triable then
          etatTri.update {
            case Some((i, croissant)) if i == index => Some((index, !croissant))
            case _                                  => Some((index, false))
          }
      },
      span(cls := "hm-th-libelle", colonne.libelle),
      child.maybe <-- etatTri.signal.map {
        case Some((i, croissant)) if i == index =>
          Some(span(cls := "hm-caret", cls.toggle("est-haut") := croissant))
        case _ => None
      }
    )
