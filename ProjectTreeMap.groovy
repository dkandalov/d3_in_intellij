import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage

import static http.Util.restartHttpServer
import static ru.intellijeval.PluginUtil.show
import static ru.intellijeval.PluginUtil.showInConsole

/**
 * User: dima
 * Date: 18/11/2012
 */
class ProjectTreeMap {
	static showFor(Project project) {
		def rootManager = ProjectRootManager.getInstance(project)
		def psiManager = PsiManager.getInstance(project)
		def directoryService = JavaDirectoryService.instance

		def rootPackages = rootManager.contentSourceRoots.collectMany{ contentRoot ->
			def subdirectories = psiManager.findDirectory(contentRoot).subdirectories
			subdirectories.collect{ directoryService.getPackage(it) }
		}.unique()

		showInConsole(rootPackages.collect{ PsiPackage psiPackage -> psiPackage.name + ":" + psiPackage.subPackages.collect{it.name} }.join("\n"), project)
		def rootContainer = new Container("/", rootPackages.collect{ convertToContainerHierarchy(it) })

		//showInConsole(rootContainer.toString(), event.project)

		def server = restartHttpServer("ProjectTreeMap_HttpServer", ["/treemap.json": {
			try {
				rootContainer.toJSON()
			} catch (Exception e) {
				show(e.toString())
			}
		}])
		BrowserUtil.launchBrowser("http://localhost:${server.port}/treemap.html")
	}


	private static def Element convertToElement(PsiClass psiClass) {
		new Element(psiClass.name, psiClass.allFields.size() + psiClass.allMethods.size() + psiClass.allInnerClasses.size())
	}

	private static def convertToContainerHierarchy(PsiPackage psiPackage) {
		new Container(
				psiPackage.name,
				psiPackage.classes.collect{convertToElement(it)} + psiPackage.subPackages.collect{convertToContainerHierarchy(it)}
		)
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
