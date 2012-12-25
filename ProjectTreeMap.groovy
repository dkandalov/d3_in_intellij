import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager

import javax.swing.*

import static http.Util.restartHttpServer
import static ru.intellijeval.PluginUtil.showExceptionInConsole
import static ru.intellijeval.PluginUtil.showInConsole

/**
 * User: dima
 * Date: 18/11/2012
 */
class ProjectTreeMap {
	static showFor(Project project) {
		CharSequence s = ""
		new Task.Backgroundable(project, "Preparing tree map...", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
			@Override void run(ProgressIndicator indicator) {
				ApplicationManager.application.runReadAction {
					try {
//						Container rootContainer = createPackageAndClassTree(project)
//						s = rootContainer.toJSON()

						def rootFolders = rootFoldersIn(project)
						StringBuilder stringBuilder = new StringBuilder()
						stringBuilder.append('{"name": "/", "children": [')
						appendChildrenAsJSON(stringBuilder, rootFolders)
						stringBuilder.append(']}')

						s = stringBuilder

//						SwingUtilities.invokeLater{ showInConsole(s.toString(), project) }
					} catch (Exception e) {
						showExceptionInConsole(e, project)
					}
				}
			}

			@Override void onSuccess() {
				def server = restartHttpServer("ProjectTreeMap_HttpServer",
						sampleHardcodedHandler(),
						{ Exception e ->
							SwingUtilities.invokeLater{ showExceptionInConsole(e, project) }
						}
				)
				BrowserUtil.launchBrowser("http://localhost:${server.port}/treemap.html")
			}

			static Closure actualHandler() {
				{ requestURI ->
					// TODO
				}
			}

			static Closure sampleHardcodedHandler() {
				{ requestURI ->
					[
						"/.json":
							"""
{"name": "",
 "size": 100,
 "children": [
    {
	    "name": "ru",
	    "size": 100,
	    "hasChildren": true
    }
 ]
}"""
						,
						"/ru.json":
							"""
{"name": "ru",
 "size": 100,
 "children": [
    {
	    "name": "intellijeval",
	    "size": 100,
	    "hasChildren": true
    }
 ]
}"""
						,
						"/ru.intellijeval.json":
							"""
{"name": "ru.intellijeval",
 "size": 100,
 "children": [
    {
	    "name": "toolwindow",
	    "size": 40,
	    "hasChildren": true
    },
    {
        "name": "EvalComponent",
        "size": 34
    },
    {
        "name": "EvalErrorReporter",
        "size": 19
    }
 ]
}"""
				].get(requestURI)
				}
			}
		}.queue()
	}

	static CharSequence appendChildrenAsJSON(StringBuilder stringBuilder, Collection files, PsiDirectory parent = null) {
		files.each { file ->
			if (file.directory) {
				stringBuilder.append('{"name": "' + file.name + '", "children": [')
				appendChildrenAsJSON(stringBuilder, file.children.toList(), file)
				stringBuilder.append(']},')
			}
		}
		if (parent != null) {
			JavaDirectoryService.instance.getClasses(parent).each {
				stringBuilder.append('{"name": "' + it.name + '", "size": ' + sizeOf(it) + '},')
			}
		}

		if (stringBuilder.lastIndexOf(",") == stringBuilder.length() - 1)
			stringBuilder.deleteCharAt(stringBuilder.length() - 1)
		stringBuilder
	}

	public static Container createPackageAndClassTree(Project project) {
		def rootFolders = rootFoldersIn(project)
		SwingUtilities.invokeLater {
			showInConsole(rootFolders.collect { PsiDirectory directory -> directory.name }.join("\n"), project)
		}

		def rootContainer = new Container("/", rootFolders.collect{ convertToContainerHierarchy(it) })
		rootContainer
	}

	public static Collection rootFoldersIn(Project project) {
		def psiManager = PsiManager.getInstance(project)

		ProjectRootManager.getInstance(project).contentSourceRoots.collectMany{ sourceRoot ->
			sourceRoot.children.findAll{it.directory}.collect{ psiManager.findDirectory(it) }.findAll{it != null}
		}.unique()
	}

	private static def convertToContainerHierarchy(PsiDirectory directory) {
		def directoryService = JavaDirectoryService.instance

		def classes = { directoryService.getClasses(directory).collect{ convertToElement(it) } }
		def packages = { directory.children.findAll{it instanceof PsiDirectory}.collect{ convertToContainerHierarchy(it) } }

		new Container(directory.name, classes() + packages())
	}

	private static def Element convertToElement(PsiClass psiClass) {
		new Element(psiClass.name, sizeOf(psiClass))
	}

	public static int sizeOf(PsiClass psiClass) {
		psiClass.allFields.size() + psiClass.allMethods.size() + psiClass.allInnerClasses.size()
	}

	private static class Container {
		final String name
		final List children

		Container(String name, List children) {
			this.name = name
			this.children = children
		}

		String toJSON(shift = 0) {
			('\t' * shift) +
			"{\"name\": \"$name\", " +
			"\"children\": [\n" + children.collect {it.toJSON(shift + 1)}.join(',\n') +
			"\n${'\t' * shift}]}"
		}
	}

	private static class Element {
		final String name
		final int size

		Element(String name, int size) {
			this.name = name
			this.size = size
		}

		String toJSON(shift = 0) {
			('\t' * shift) + "{\"name\": \"$name\", \"size\": $size}"
		}
	}
}
