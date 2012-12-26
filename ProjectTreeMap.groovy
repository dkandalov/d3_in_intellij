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
 * TODO:
 *  - ask whether to recalculate treemap (better calculate if it's null; have separate action to recalculate it)
 *  - collapse one-child containers
 *  - make sure class size calculation makes sense (count statements?)
 *  - show size under package/class name
 *  - fix not-removed svgs in UI
 *
 *  - packages and classes should look differently in tree map (different color schemes? bold/bigger font for packages?)
 *  - clickable breadcrumbs (e.g. to quickly navigate several methods up)
 *  - popup hints for small rectangles in treemap (otherwise it's impossible to read package/class name)
 *  - open treemap based on currently selected item in project view or currently open file
 *  - reduce breadcrumbs font size when it doesn't fit on screen?
 *  - break up classes into methods?
 *
 * User: dima
 * Date: 18/11/2012
 */
class ProjectTreeMap {
	private static Container rootContainer = null // TODO will it be GCed on plugin reload?

	static showFor(Project project) {
		rootContainer = null

		new Task.Backgroundable(project, "Preparing tree map...", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
			@Override void run(ProgressIndicator indicator) {
				ApplicationManager.application.runReadAction {
					try {
						rootContainer = new PackageAndClassTreeBuilder(project).buildTree()
					} catch (Exception e) {
						showExceptionInConsole(e, project)
					}
				}
			}

			@Override void onSuccess() {
				def server = restartHttpServer("ProjectTreeMap_HttpServer",
						{ String requestURI -> new RequestHandler(rootContainer).onRequest(requestURI) },
						{ Exception e -> SwingUtilities.invokeLater{ showExceptionInConsole(e, project) } }
				)
				BrowserUtil.launchBrowser("http://localhost:${server.port}/treemap.html")
			}

		}.queue()
	}

	private static class RequestHandler {
		private final Container rootContainer

		RequestHandler(Container rootContainer) {
			this.rootContainer = rootContainer
		}

		def onRequest(String requestURI) {
			if (!requestURI.endsWith(".json")) return

			def containerFullName = requestURI.replace(".json", "").replaceFirst("/", "")
			List<String> namesList = splitName(containerFullName)
			findContainerByName(namesList, rootContainer)?.toJSON(containerFullName)
		}

		private static List<String> splitName(String containerFullName) {
			def namesList = containerFullName.split(/\./).toList()
			if (namesList.size() > 0 && namesList.first() != "") {
				// this is because for "" split returns [""] but for "foo" returns ["foo"], i.e. list without first ""
				namesList.add(0, "")
			}
			namesList
		}

		private static Container findContainerByName(List namesList, Container container) {
			if (container == null || namesList.empty || namesList.first() != container.name) return null
			if (namesList.size() == 1 && namesList.first() == container.name) return container

			for (child in container.children) {
				def result = findContainerByName(namesList.tail(), child)
				if (result != null) return result
			}
			null
		}
	}

	private static class PackageAndClassTreeBuilder {
		private final Project project

		PackageAndClassTreeBuilder(Project project) {
			this.project = project
		}

		public Container buildTree() {
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

		private static Container convertToElement(PsiClass psiClass) { new Container(psiClass.name, sizeOf(psiClass)) }

		private static int sizeOf(PsiClass psiClass) { psiClass.allFields.size() + psiClass.allMethods.size() + psiClass.allInnerClasses.size() }
	}

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
