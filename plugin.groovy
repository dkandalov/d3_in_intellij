import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

import static ProjectTreeMap.*
import static ru.intellijeval.PluginUtil.*

//WordCloud.showFor(event.project)
ProjectTreeMap.showFor((Project) event.project)

if (true) return

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