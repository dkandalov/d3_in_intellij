import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

import javax.swing.SwingUtilities

import static http.Util.restartHttpServer
import static intellijeval.PluginUtil.*

/**
 * User: dima
 * Date: 18/11/2012
 */
class WordCloud {
	static def showFor(Project project, String pluginPath) {
		String wordsAsJSON = ""

		new Task.Backgroundable(project, "Preparing word cloud...", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
			@Override void run(ProgressIndicator indicator) {
				ApplicationManager.application.runReadAction {
					try {
						//Map wordOccurrences = new TextualWordOccurrences().calculateFor(project, indicator)
						Map wordOccurrences = new IdentifiersOccurrences().calculateFor(project, indicator)

						wordsAsJSON = convertToJSON(wordOccurrences)

						SwingUtilities.invokeLater{
							def output = wordOccurrences.entrySet().sort{it.value}.join("\n")
							showInConsole(output, project)
						}
					} catch (Exception e) {
						showExceptionInConsole(e, project)
					}
				}
			}

			@Override void onSuccess() {
				def handler = { ["/words.json": wordsAsJSON].get(it) }
				def server = restartHttpServer("WordCloud_HttpServer", pluginPath, handler, { it.printStackTrace() })
				BrowserUtil.launchBrowser("http://localhost:${server.port}/wordcloud.html")
			}
		}.queue()
	}

	static String convertToJSON(Map wordOccurrences) {
		if (wordOccurrences.isEmpty()) return '{"words": []}'

		def min = wordOccurrences.min { it.value }.value
		def max = wordOccurrences.max { it.value }.value
		def normalizedSizeOf = { entry ->
			def word = entry.key
			def size = entry.value
			if (word.size() < 5) size += (max * 0.3) // this is to make shorter words more noticeable

			Math.round((double) 5 + ((size - min) * 75 / (max - min)))
		}
		"""{"words": [
${wordOccurrences.entrySet().sort{ -it.value }.take(600).collect { '{"text": "' + it.key + '", "size": ' + normalizedSizeOf(it) + '}' }.join(",\n")}
]}
"""
	}

	private interface WordCloudSource {
		Map<String, Integer> calculateFor(Project project, ProgressIndicator indicator)
	}

	private static class IdentifiersOccurrences implements WordCloudSource {
		@Override Map<String, Integer> calculateFor(Project project, ProgressIndicator indicator) {
			def occurrences = new HashMap<String, Integer>().withDefault { 0 }
			forEachClassIn(project) { PsiClass psiClass ->
				forEachIdentifierIn(psiClass) { PsiIdentifier psiIdentifier ->
					occurrences.put(psiIdentifier.text, occurrences[psiIdentifier.text] + 1)
				}
			}
			occurrences
		}

		private static forEachIdentifierIn(PsiClass psiClass, Closure closure) {
			psiClass.accept(new PsiRecursiveElementVisitor() {
				@Override void visitElement(PsiElement element) {
					if (element instanceof PsiIdentifier) closure.call(element)
					super.visitElement(element)
				}
			})
		}

		private static forEachClassIn(Project project, Closure closure) {
			sourceRootDirectoriesIn(project).each { PsiDirectory psiDirectory ->
				visitAllClassesIn(psiDirectory, closure)
			}
		}

		private static visitAllClassesIn(PsiDirectory psiDirectory, Closure closure) {
			def directoryService = JavaDirectoryService.instance
			directoryService.getClasses(psiDirectory).each{ closure.call(it) }
			psiDirectory.children.findAll{it instanceof PsiDirectory}.each{ visitAllClassesIn((PsiDirectory) it, closure) }
		}

		private static Collection<PsiDirectory> sourceRootDirectoriesIn(Project project) {
			def psiManager = PsiManager.getInstance(project)
			ProjectRootManager.getInstance(project).contentSourceRoots.collect{ psiManager.findDirectory(it) }
		}
	}

	static class TextualWordOccurrences implements WordCloudSource {
		@Override Map<String, Integer> calculateFor(Project project, ProgressIndicator indicator) {
			def wordOccurrences = new HashMap<String, Integer>().withDefault { 0 }
			def rootManager = ProjectRootManager.getInstance(project)

			rootManager.fileIndex.iterateContent(new ContentIterator() {
				@Override boolean processFile(VirtualFile file) {
					if (indicator.canceled) return false
					analyzeFile(file, wordOccurrences)
				}
			})
			wordOccurrences.entrySet().removeAll { it.key == "def" || it.key == "new" }
			wordOccurrences
		}

		private static boolean analyzeFile(VirtualFile file, wordOccurrences) {
			if (file.isDirectory()) return true
			if (file.extension != "groovy" && file.extension != "java") return true

			def text = file.inputStream.readLines()

			// drop apache license header
			if (text.size() > 2 &&
					text[0].contains("/*") &&
					text[1].contains("Copyright 2000-2012 JetBrains s.r.o.")) {
				text = text.drop(15)
			}

			text.each { line ->
				if (line.startsWith("import")) return
				line.split(/[\s!{}\[\]+-<>()\/\\,"'@&$=*]/).findAll { !it.empty }.each { word ->
					wordOccurrences.put(word, wordOccurrences[word] + 1)
				}
			}
			true
		}

	}
}
