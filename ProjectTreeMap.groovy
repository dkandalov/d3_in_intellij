import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiStatement
import http.SimpleHttpServer

import javax.swing.*

import static http.Util.restartHttpServer
import static ru.intellijeval.PluginUtil.showExceptionInConsole
import static ru.intellijeval.PluginUtil.showInConsole
import static ru.intellijeval.PluginUtil.*

/**
 * TODO:
 *  + compact one-child containers
 *  + make sure class size calculation makes sense (count statements?)
 *  - open treemap based on currently selected item in project view or currently open file
 *  + show size under package/class name
 *  - fix not-removed svgs in UI and other UI exceptions
 *  - ask whether to recalculate treemap (better calculate if it's null; have separate action to recalculate it)
 *
 *  - make sure it works with multiple projects (per-project treemap cache)
 *  - packages and classes should look differently in tree map (different color schemes? bold/bigger font for packages?)
 *  - clickable breadcrumbs (e.g. to quickly navigate several methods up)
 *  - popup hints for small rectangles in treemap (otherwise it's impossible to read package/class name)
 *  - reduce breadcrumbs font size when it doesn't fit on screen?
 *  - break up classes into methods?
 *
 * User: dima
 * Date: 18/11/2012
 */
class ProjectTreeMap {
	private static final Logger LOG = Logger.getInstance(ProjectTreeMap.class);

	// TODO this should be a container per project
	// TODO must be cached like http server is cached (otherwise it's not really cached)
	private static Container rootContainer = null // TODO will it be GCed on plugin reload?

	static initActions() {
		registerAction("ProjectTreeMap-Show", "alt T") { AnActionEvent event ->
			ensureRootContainerInitialized(event.project) {
				SimpleHttpServer server = restartHttpServer()
				BrowserUtil.launchBrowser("http://localhost:${server.port}/treemap.html")
			}
		}
		registerAction("ProjectTreeMap-RecalculateTree", "alt R") { AnActionEvent event ->
//			if (rootContainer != null) {
//				def userResponse = JOptionPane.showConfirmDialog(null, "Reset project treemap?", "TreeMap", JOptionPane.YES_NO_OPTION)
//				if (userResponse != JOptionPane.YES_OPTION) return
//			}

			rootContainer = null
			ensureRootContainerInitialized(event.project) {
				show("Recalculated project tree map")
			}
		}
		show("Registered ProjectTreeMap actions")
	}

	private static ensureRootContainerInitialized(Project project, Closure closure) {
		if (rootContainer != null) return closure.call()

		new Task.Backgroundable(project, "Building tree map index for project...", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
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
				closure.call()
			}

		}.queue()
	}

	private static  SimpleHttpServer restartHttpServer() {
		def server = restartHttpServer("ProjectTreeMap_HttpServer",
				{ String requestURI -> new RequestHandler(rootContainer).handleRequest(requestURI) },
				{ Exception e -> SwingUtilities.invokeLater { ProjectTreeMap.LOG.error("", e) } }
		)
		server
	}

	static class RequestHandler {
		private final boolean skipEmptyMiddlePackages
		private final Container rootContainer

		RequestHandler(Container rootContainer, boolean skipEmptyMiddlePackages = true) {
			this.rootContainer = rootContainer
			this.skipEmptyMiddlePackages = skipEmptyMiddlePackages
		}

		String handleRequest(String requestURI) {
			if (!requestURI.endsWith(".json")) return

			def containerRequest = requestURI.replace(".json", "").replaceFirst("/", "")

			Container container
			if (containerRequest == "") {
				container = rootContainer
			} else if (containerRequest.startsWith("parent-of/")) {
				containerRequest = containerRequest.replaceFirst("parent-of/", "")
				List<String> path = splitName(containerRequest)
				container = findContainer(path, rootContainer).parent

				if (skipEmptyMiddlePackages) {
					while (container != null && container != rootContainer && container.children.size() == 1)
						container = container.parent
				}
				if (container == null) return rootContainer
			} else {
				List<String> path = splitName(containerRequest)
				container = findContainer(path, rootContainer)
				if (skipEmptyMiddlePackages) {
					while (container.children.size() == 1 && container.children.first().children.size() > 0)
						container = container.children.first()
				}
			}
			container?.toJSON()
		}

		private static List<String> splitName(String containerFullName) {
			def namesList = containerFullName.split(/\./).toList()
			if (namesList.size() > 0 && namesList.first() != "") {
				// this is because for "" split returns [""] but for "foo" returns ["foo"], i.e. list without first ""
				namesList.add(0, "")
			}
			namesList
		}

		private static Container findContainer(List path, Container container) {
			if (container == null || path.empty || path.first() != container.name) return null
			if (path.size() == 1 && path.first() == container.name) return container

			for (child in container.children) {
				def result = findContainer(path.tail(), child)
				if (result != null) return result
			}
			null
		}
	}

	static class PackageAndClassTreeBuilder {
		private final Project project

		PackageAndClassTreeBuilder(Project project) {
			this.project = project
		}

		public Container buildTree() {
			def rootFolders = rootFoldersIn(project)
			def rootChildren = rootFolders.collect { convertToContainerHierarchy(it).withName(it.parent.name + "/" + it.name) }
			new Container("", rootChildren)
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

		private static sizeOf(PsiClass psiClass) {
			if (psiClass.containingFile instanceof PsiJavaFile)
				return new JavaClassEstimator().sizeOf(psiClass)
			else
				return new GenericClassEstimator().sizeOf(psiClass)
		}
	}

	/**
	 * Plugins for other languages (e.g. Groovy) which extend PsiClass even though it's psi for "java class"
	 */
	static class GenericClassEstimator {
		int sizeOf(PsiClass psiClass) {
			psiClass.text.split("\n").findAll{ it.trim() != "" }.size()
		}
	}

	static class JavaClassEstimator {
		int sizeOf(PsiClass psiClass) {
			if (!psiClass.containingFile instanceof PsiJavaFile)
				throw new IllegalArgumentException("$psiClass is not a Java class")

			int statementCount = 0
			psiClass.accept(new PsiRecursiveElementVisitor() {
				@Override void visitElement(PsiElement element) {
					if (element instanceof PsiStatement) statementCount++
					super.visitElement(element)
				}
			})

			1 + // this is to account for class declaration
			statementCount +
			psiClass.fields.size() +
			psiClass.initializers.size() +
			psiClass.constructors.sum(0){ sizeOfMethod((PsiMethod) it) } +
			psiClass.methods.sum(0){ sizeOfMethod((PsiMethod) it) } +
			psiClass.innerClasses.sum(0){ sizeOf((PsiClass) it) }
		}

		private static int sizeOfMethod(PsiMethod psiMethod) { 1 + psiMethod.parameterList.parametersCount }
	}

	static class Container {
		final String name
		final Container[] children
		final int size
		private Container parent = null

		Container(String name, Collection<Container> children) {
			this(name, (Container[]) children.toArray(), sumOfChildrenSizes(children))
		}

		Container(String name, Container[] children = new Container[0], int size) {
			this.name = name
			this.children = children
			this.size = size

			for (child in this.children) child.parent = this
		}

		Container withName(String newName) {
			new Container(newName, children, size)
		}

		static int sumOfChildrenSizes(Collection<Container> children) {
			// this is an attempt to optimize groovy by not using .sum(Closure) method
			int sum = 0
			for (Container child in children) sum += child.size
			sum
		}

		Container getParent() {
			this.parent
		}

		private String getFullName() {
			if (parent == null) name
			else parent.fullName + "." + name
		}

		String toJSON(int level = 0) {
			String childrenAsJSON
			String jsonName
			if (level == 0) {
				childrenAsJSON = "\"children\": [\n" + children.collect { it.toJSON(level + 1) }.join(',\n') + "]"
				jsonName = fullName
			} else {
				childrenAsJSON = "\"hasChildren\": " + (children.size() > 0 ? "true" : "false")
				jsonName = name
			}

			"{" +
			"\"name\": \"$jsonName\", " +
			"\"size\": \"$size\", " +
			childrenAsJSON +
			"}"
		}
	}
}
