package http

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class Util {
	static def exampleOfUsage() {
		resetCached("GetStringId")
		getCachedOrCreate("GetStringId") { prevValue ->
			if (prevValue != null) {
//				show(prevValue)
				prevValue
			} else {
//				show("newwww!!!")
				"Cached string!! ${new Random().nextInt(100)}"
			}
		}
	}

	static def <T> T getCachedOrCreate(String id, Closure callback) {
		def actionManager = ActionManager.instance

		def action = actionManager.getAction(id)
		T newValue
		if (action != null) {
			actionManager.unregisterAction(id)
			def prevValue = action.value
			newValue = (T) callback.call(prevValue)
		} else {
			newValue = (T) callback.call(null)
		}

		actionManager.registerAction(id, new AnAction() {
			final def value = newValue

			@Override void actionPerformed(AnActionEvent e) {}
		})
		newValue
	}

	static def resetCached(String id) {
		ActionManager.instance.unregisterAction(id)
	}

	public static SimpleHttpServer restartHttpServer(String id, Map map) {
		getCachedOrCreate(id) { previousServer ->
			if (previousServer != null) {
//				PluginUtil.show("Prev server: " + previousServer)
				previousServer.stop()
			}

			def server = new SimpleHttpServer()
			def started = false
			for (port in (8100..10000)) {
				try {
					server.start(port, map)
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

