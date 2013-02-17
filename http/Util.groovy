package http

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import static intellijeval.PluginUtil.*

class Util {
	static def exampleOfUsage() {
		resetCached("GetStringId")
		getCachedBy("GetStringId") { prevValue ->
			if (prevValue != null) {
				prevValue
			} else {
				"Cached string!! ${new Random().nextInt(100)}"
			}
		}
	}

	static def resetCached(String id) {
		ActionManager.instance.unregisterAction(id)
	}

	public static SimpleHttpServer restartHttpServer(String id, String pluginPath, Closure handler, Closure errorListener) {
		getCachedBy(id) { previousServer ->
			if (previousServer != null) {
				previousServer.stop()
			}

			def server = new SimpleHttpServer()
			def started = false
			for (port in (8100..10000)) {
				try {
					server.start(port, pluginPath, handler, errorListener)
					started = true
					break
				} catch (BindException ignore) {
				}
			}
			if (!started) throw new IllegalStateException("Failed to start server '${id}'")
			server
		}
	}
}

