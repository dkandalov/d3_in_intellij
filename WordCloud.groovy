import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

import static http.Util.restartHttpServer

/**
 * User: dima
 * Date: 18/11/2012
 */
class WordCloud {
	static def showFor(Project project) {
		String wordsAsJSON = ""

		new Task.Backgroundable(project, "Preparing word cloud...", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
			@Override void run(ProgressIndicator indicator) {
				Map wordOccurrences = calculateWordOccurrences(project, indicator)
				wordsAsJSON = convertToJSON(wordOccurrences)

				//def output = wordOccurrences.entrySet().sort{it.value}.join("\n")
				//showInConsole("${output}", event.project)
			}

			@Override void onSuccess() {
				def handler = { ["/words.json": wordsAsJSON].get(it) }
				def server = restartHttpServer("WordCloud_HttpServer", handler, { it.printStackTrace() })
				BrowserUtil.launchBrowser("http://localhost:${server.port}/wordcloud.html")
			}
		}.queue()
	}

	private static Map calculateWordOccurrences(Project project, ProgressIndicator indicator) {
		def wordOccurrences = new HashMap().withDefault { 0 }
		def rootManager = ProjectRootManager.getInstance(project)

		rootManager.fileIndex.iterateContent(new ContentIterator() {
			@Override boolean processFile(VirtualFile file) {
				if (indicator.canceled) return false
				if (file.isDirectory()) return true
				if (file.extension != "groovy" && file.extension != "java") return true

				def text = file.inputStream.readLines()
				text.each { line ->
					if (line.startsWith("import")) return

					line.split(/[\s!{}\[\]+-<>()\/\\,"'@&$=*]/).findAll { !it.empty }.each { word ->
						wordOccurrences.put(word, wordOccurrences.get(word) + 1)
					}
				}
				true
			}
		})
		wordOccurrences.entrySet().removeAll { ["def", "new"].contains(it.key) }
		wordOccurrences
	}

	static String convertToJSON(Map wordOccurrences) {
		def min = wordOccurrences.min { it.value }.value
		def max = wordOccurrences.max { it.value }.value
		def normalizedSizeOf = { entry ->
			def word = entry.key
			def size = entry.value
			if (word.size() < 5) size += (max * 0.3) // this is to make shorter words more noticeable

			Math.round((double) 5 + ((size - min) * 75 / (max - min)))
		}
		"""{"words": [
${wordOccurrences.entrySet().sort { -it.value }.take(100).collect { '{"text": "' + it.key + '", "size": ' + normalizedSizeOf(it) + '}' }.join(",\n")}
]}
"""
	}
}
