package ali

import cats.effect.{ExitCode, IO, IOApp}
import fs2.io.tcp._
import cats.effect.ContextShift
import cats.effect.Concurrent
import java.net.InetSocketAddress
import fs2.{Chunk, Stream}
import fs2.text
import cats.effect.Blocker
import cats.implicits._
import cats.effect._

object Main extends IOApp {

    def echoServer[F[_]: ContextShift](socketGroup: SocketGroup)(implicit F: Concurrent[F]): F[Unit] = {

      val clientConnectionStream: Stream[F, Resource[F, Socket[F]]] = socketGroup.server(new InetSocketAddress("localhost", 5556))

      clientConnectionStream.map { clientResource =>
        Stream.eval(F.delay(println("connection received"))).drain ++ Stream
          .resource(clientResource) // Stream[F, Socket]
          .flatMap { client =>
            client.reads(8192) // Stream[Byte]
              .through(text.utf8Decode) // Stream[String]
              .through(text.lines) // Stream[String]
              .debug()
              .interleave(Stream.constant("\n")) // Stream[String]
              .through(text.utf8Encode) // Stream[Byte]
              .through(client.writes()) // Stream[Unit]
          } // Stream[F, Unit]
      }.parJoin(100).compile.drain
  }

    def run(args: List[String]): IO[ExitCode] = 
      Blocker[IO].use { blocker =>
        SocketGroup[IO](blocker).use { socketGroup =>
          echoServer[IO](socketGroup)
        }
      }.as(ExitCode.Success)
}