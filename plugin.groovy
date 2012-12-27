import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager

import static ProjectTreeMap.JavaClassEstimator
import static ru.intellijeval.PluginUtil.*


//WordCloud.showFor(event.project)
ProjectTreeMap.initActions()


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