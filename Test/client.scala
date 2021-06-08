package ali

import cats.effect.IOApp
import fs2._
import cats.effect._
import fs2.io.tcp.SocketGroup
import java.net.InetSocketAddress
import cats.implicits._
import fs2.io.tcp.Socket

object Client extends IOApp {

  def consoleOut(s: Byte): IO[Unit] = IO{println(s)}

  def socketWriter: Socket[IO] => Stream[IO, Unit]= socket => {
    val hiButEncoded = 
         Stream.iterate("Hi find me")(identity)
           .take(50)
           .through(text.utf8Encode)
           .covary[IO]
           .debug(b => "AFTER ENCODING")

    hiButEncoded.through(socket.writes()).debug(b => s"After Writer $b")
  }

  def socketReader: Socket[IO] => Stream[IO, Unit] = socket => {
    socket.reads(8192).debug(b => s"AFTER READ $b").evalMap(e => consoleOut(e)).debug(b => s"AFTER Console $b")
  }

  def run(args: List[String]): IO[ExitCode] = {
      (Blocker[IO].use { blocker =>
        SocketGroup[IO](blocker).use { socketGroup =>
            socketGroup.client[IO](new InetSocketAddress("localhost", 5555)).use { socket =>
                (socketWriter(socket) ++ Stream.eval(socket.endOfOutput) ++ socketReader(socket)).compile.drain
            }
        }
      } >> IO(println("Done"))).as(ExitCode.Success)
  }

}
