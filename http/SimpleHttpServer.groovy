package http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import java.util.concurrent.Executors

import static ru.intellijeval.EvalComponent.pluginToPathMap

/**
 * User: dima
 * Date: 17/10/2012
 */
class SimpleHttpServer {
	int port
	private HttpServer server

	static void main(String[] args) throws IOException {
		def server = new SimpleHttpServer()
		server.start(8084)
	}

	void start(int port = 8100, Map<String, Closure> mapping = [:]) {
		this.port = port

		server = HttpServer.create(new InetSocketAddress(port), 0)
		server.createContext("/", new MyHandler(mapping))
		server.executor = Executors.newCachedThreadPool()
		server.start()
	}
	
	void stop() {
		if (server != null) server.stop(0)
	}

	static class MyHandler implements HttpHandler {
		private final Map<String, Closure> mapping

		MyHandler(Map<String, Closure> mapping) {
			this.mapping = mapping
		}

		@Override void handle(HttpExchange exchange) {
			new Exchanger(exchange).with {
				try {
					println(requestURI)

					if (this.mapping.containsKey(requestURI)) {
						replyWithText(this.mapping[requestURI].call().toString())
					} else if (requestURI.startsWith("/") && requestURI.size() > 1) {
						// TODO replace with "path" parameter
						def file = new File(pluginToPathMap().get("tagcloud") + "/http" + "${requestURI.toString()}")
						if (!file.exists()) {
							replyNotFound()
						} else {
							replyWithText(file.readLines().join("\n"), guessContentTypeOf(file))
						}
					} else {
						replyNotFound()
					}
				} catch (Exception e) {
					e.printStackTrace()
				}
			}
		}

		static String guessContentTypeOf(File file) {
			if (file.name.endsWith(".css")) "text/css"
			else if (file.name.endsWith(".js")) "text/javascript"
			else if (file.name.endsWith(".html")) "text/html"
			else "text/plain"
		}

		private static class Exchanger {
			private HttpExchange exchange

			Exchanger(HttpExchange exchange) {
				this.exchange = exchange
			}

			String getRequestURI() {
				exchange.requestURI.toString()
			}

			void replyWithText(String text, String contentType = "text/plain") {
				exchange.responseHeaders.set("Content-Type", contentType)
				exchange.sendResponseHeaders(200, 0)
				exchange.responseBody.write(text.bytes)
				exchange.responseBody.close()
			}

			void replyNotFound() {
				exchange.sendResponseHeaders(404, 0)
				exchange.responseBody.close()
			}
		}
	}
}
