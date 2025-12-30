package io.vault.mobile.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import io.vault.mobile.R
import io.vault.mobile.data.local.PreferenceManager
import io.vault.mobile.data.repository.VaultRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VaultAutofillService : AutofillService() {

    @Inject
    lateinit var repository: VaultRepository

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.last().structure
        val packageName = structure.activityComponent.packageName

        serviceScope.launch {
            val isEnabled = preferenceManager.autofillEnabled.first()
            if (!isEnabled) {
                callback.onSuccess(null)
                return@launch
            }

            val entry = repository.getEntryByPackageName(packageName)
            if (entry != null) {
                val response = FillResponse.Builder()
                
                val dataset = Dataset.Builder()
                val password = repository.decryptPassword(entry.encryptedPassword)
                
                // Traverse structure to find relevant fields
                // This is a simplified traversal. Real-world apps use more robust matching.
                val fillNodes = findFillableNodes(structure)
                
                var datasetAdded = false
                fillNodes.forEach { (id, type) ->
                    when (type) {
                        AutofillNodeType.USERNAME -> {
                            dataset.setValue(id, AutofillValue.forText(entry.username), 
                                createPresentation(entry.username))
                            datasetAdded = true
                        }
                        AutofillNodeType.PASSWORD -> {
                            dataset.setValue(id, AutofillValue.forText(password), 
                                createPresentation("********"))
                            datasetAdded = true
                        }
                    }
                }

                if (datasetAdded) {
                    response.addDataset(dataset.build())
                    callback.onSuccess(response.build())
                } else {
                    callback.onSuccess(null)
                }
            } else {
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Optionally implement to detect new passwords being entered
        callback.onSuccess()
    }

    private fun findFillableNodes(structure: AssistStructure): Map<AutofillId, AutofillNodeType> {
        val nodes = mutableMapOf<AutofillId, AutofillNodeType>()
        val nodesCount = structure.windowNodeCount
        for (i in 0 until nodesCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            traverseNode(rootNode, nodes)
        }
        return nodes
    }

    private fun traverseNode(node: AssistStructure.ViewNode, nodes: MutableMap<AutofillId, AutofillNodeType>) {
        val autofillHints = node.autofillHints
        if (autofillHints != null) {
            if (autofillHints.contains("username") || autofillHints.contains("emailAddress")) {
                node.autofillId?.let { id -> nodes[id] = AutofillNodeType.USERNAME }
            } else if (autofillHints.contains("password")) {
                node.autofillId?.let { id -> nodes[id] = AutofillNodeType.PASSWORD }
            }
        }

        val childrenCount = node.childCount
        for (i in 0 until childrenCount) {
            traverseNode(node.getChildAt(i), nodes)
        }
    }

    private fun createPresentation(text: String): RemoteViews {
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
        presentation.setTextViewText(android.R.id.text1, "Cyber Vault: $text")
        return presentation
    }

    enum class AutofillNodeType { USERNAME, PASSWORD }
}
