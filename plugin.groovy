import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager

import static intellijeval.PluginUtil.registerAction
import static intellijeval.PluginUtil.show
import static ProjectTreeMap.JavaClassEstimator


registerAction("WordCloud", "ctrl alt shift T") { AnActionEvent event ->
	WordCloud.showFor(event.dataContext, pluginPath)
}
//ProjectTreeMap.initActions(pluginPath)
//show(pluginPath)
show("reloaded")

if (true) return
// the code below is to experiment and play with java classes size estimation

List<PsiClass> psiJavaClassesInOpenEditor(Project project) {
	VirtualFile virtualFile = FileEditorManagerEx.getInstance(project).currentFile
	def psiManager = PsiManager.getInstance(project)
	def psiFile = psiManager.findFile(virtualFile)
	if (psiFile == null) return []

	if (psiFile instanceof PsiJavaFile) {
		psiFile.classes.toList()
	} else {
		[]
	}
}

List<PsiClass> psiClasses = psiJavaClassesInOpenEditor((Project) event.project)
psiClasses.each { PsiClass psiClass ->
	def size = new JavaClassEstimator().sizeOf(psiClass)
	show(size.toString() + " " + psiClass.name)
}