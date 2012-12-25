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
import static ru.intellijeval.PluginUtil.*

/**
 * User: dima
 * Date: 18/11/2012
 */
class ProjectTreeMap {
	private static Container rootContainer = null

	static showFor(Project project) {
		rootContainer = null

		new Task.Backgroundable(project, "Preparing tree map...", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
			@Override void run(ProgressIndicator indicator) {
				ApplicationManager.application.runReadAction {
					try {
						rootContainer = createPackageAndClassTree(project)
//						show(rootContainer.toJSON("aaaa"))
					} catch (Exception e) {
						showExceptionInConsole(e, project)
					}
				}
			}

			@Override void onSuccess() {
				def server = restartHttpServer("ProjectTreeMap_HttpServer",
						createRequestHandler(),
						{ Exception e ->
							SwingUtilities.invokeLater{ showExceptionInConsole(e, project) }
						}
				)
				BrowserUtil.launchBrowser("http://localhost:${server.port}/treemap.html")
			}

			Closure createRequestHandler() {
				{ String requestURI ->
					if (!requestURI.endsWith(".json")) return

					def containerFullName = requestURI.replace(".json", "").replaceFirst("/", "")
					def namesList = containerFullName.split(/\./).toList()
					if (namesList.size() > 0 && namesList.first() != "") {
						namesList.add(0, "")
					}
					Container container = findContainerByName(namesList, rootContainer)
					container?.toJSON(containerFullName)
				}
			}

			static Container findContainerByName(List namesList, Container container) {
				if (container == null || namesList.empty || namesList.first() != container.name) return null
				if (namesList.size() == 1 && namesList.first() == container.name) return container

				for (child in container.children) {
					def result = findContainerByName(namesList.tail(), child)
					if (result != null) return result
				}
				null
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

	public static Container createPackageAndClassTree(Project project) {
		def rootFolders = rootFoldersIn(project)
//		SwingUtilities.invokeLater { showInConsole(rootFolders.collect { PsiDirectory directory -> directory.name }.join("\n"), project) }
		new Container("", rootFolders.collect{ convertToContainerHierarchy(it).withName(it.parent.name + "/" + it.name) })
	}

	private static Collection<PsiDirectory> rootFoldersIn(Project project) {
		def psiManager = PsiManager.getInstance(project)
		ProjectRootManager.getInstance(project).contentSourceRoots.collect{ psiManager.findDirectory(it) }
	}

	private static def convertToContainerHierarchy(PsiDirectory directory) {
		def directoryService = JavaDirectoryService.instance

		def classes = { directoryService.getClasses(directory).collect{ convertToElement(it) } }
		def packages = { directory.children.findAll{it instanceof PsiDirectory}.collect{ convertToContainerHierarchy(it) } }

		new Container(directory.name, classes() + packages())
	}

	private static def Container convertToElement(PsiClass psiClass) { new Container(psiClass.name, sizeOf(psiClass)) }
	private static int sizeOf(PsiClass psiClass) { psiClass.allFields.size() + psiClass.allMethods.size() + psiClass.allInnerClasses.size() }

	private static class Container {
		final String name
		final Container[] children
		final int size

		Container(String name, Collection<Container> children) {
			this.name = name
			this.children = (Container[]) children.toArray()
			this.size = sumOfChildrenSize(children)
		}

		// this is an attempt to optimize groovy by not using .sum(Closure) method
		static int sumOfChildrenSize(Collection<Container> children) {
			int sum = 0
			for (Container child in children) sum += child.size
			sum
		}

		Container(String name, Container[] children = new Container[0], int size) {
			this.name = name
			this.children = children
			this.size = size
		}

		Container withName(String newName) {
			new Container(newName, children, size)
		}

		String toJSON(String nameForJson = name, int level = 0) {
			String childrenAsJSON
			if (level == 0) childrenAsJSON = "\"children\": [\n" + children.collect { it.toJSON(it.name, level + 1) }.join(',\n') + "]"
			else childrenAsJSON = "\"hasChildren\": " + (children.size() > 0 ? "true" : "false")

			"{" +
			"\"name\": \"$nameForJson\", " +
			"\"size\": \"$size\", " +
			childrenAsJSON +
			"}"
		}
	}
}
