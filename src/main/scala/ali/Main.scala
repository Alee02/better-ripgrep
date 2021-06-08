import fs2.io.file.{readAll, walk}
import fs2.io._;
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import scala.util.matching.Regex
import cats.effect.Blocker
import java.nio.file.Paths
import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.data._
import fs2._
import fs2.{text}
import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.Bracket
import java.nio.file.Path
import fs2.io.file.FileHandle
import cats.effect.Resource
import java.nio.file.OpenOption
import java.awt.geom.GeneralPath
import java.io.File
import cats.effect.Sync
import cats.Show

object Main extends IOApp {

  case class MatchedFile(
      path: String,
      matchedLinesWithNumber: Seq[(String, Long)]
  )
  val TEST_DIRECTORY = "./"
  def argsParser(args: List[String]): Option[Regex] = args.headOption.map(_.r)

  def allFileReader[F[_]: Sync: ContextShift](path: Path, blocker: Blocker) = {
    readAll[F](path, blocker, 4096)
  }

  def walkDirectory[F[_]: Sync: ContextShift](dir: Path, blocker: Blocker) = {
    walk[F](blocker, dir)
  }

  def out[F[_]: Sync: ContextShift](blocker: Blocker) = {
    stdout(blocker)
  }

  override def run(args: List[String]): IO[ExitCode] = {

    argsParser(args) match {
      case Some(regex) =>
        Blocker[IO]
          .use { blocker =>
            val filePathStreams: Stream[IO, Path] =
              walkDirectory[IO](Paths.get(TEST_DIRECTORY), blocker)

            (Stream("Searching...") ++
            filePathStreams
              .map(path => {
                if (path.toFile().isDirectory()) { // directory check should be an IO
                  Stream.empty
                } else {
                  allFileReader[IO](path, blocker)
                    .through(text.utf8Decode)
                    .through(text.lines)
                    .zipWithIndex
                    .filter(v => regex.findFirstIn(v._1).nonEmpty)
                    .fold(List[(String, Long)]())((a, b) => a ++ List(b))
                    .filter(_.nonEmpty)
                    .map(path -> _)
                }
              })
              .parJoin(9)
              .map {
                case (path, e) => {
                  val s: String =
                    e.map { case (l, i) => s"${i + 1}: $l" }.mkString("\n")
                  s"""
                    |${path}
                    |${s}
                    """.stripMargin
                }
              })
              .through(text.utf8Encode)
              .through(out(blocker))
              .compile
              .drain
          }
          .as(ExitCode.Success)

      case None =>
        IO(println("You need to supply 1 argument argument")).as(ExitCode(2))
    }
  }

}
