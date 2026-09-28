package com.example.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectFile

@Composable
fun FileDrawer(
    files: List<ProjectFile>,
    currentFileId: String,
    onSelectFile: (ProjectFile) -> Unit,
    defaultProjectFolder: String?,
    onChangeDefaultProjectFolder: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onSaveCurrent: () -> Unit,
    onSaveAsExternal: () -> Unit,
    onOpenExternal: () -> Unit,
    onLoadTemplate: (ProjectFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = remember { ProjectFile.sampleTemplates() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
            .testTag("file_drawer")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "File Explorer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Default project folder",
                    color = Color(0xFFE6EDF3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = defaultProjectFolder ?: "Not set",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onChangeDefaultProjectFolder,
                modifier = Modifier.testTag("change_default_project_folder_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58A6FF))
            ) {
                Text(if (defaultProjectFolder == null) "Set" else "Change", fontSize = 11.sp)
            }
        }

        // Action Toolbar (Save, Save As, Open)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Save
            OutlinedButton(
                onClick = onSaveCurrent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_file_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF58A6FF))
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", fontSize = 11.sp, maxLines = 1)
            }

            // Save As (Folder of choosing)
            OutlinedButton(
                onClick = onSaveAsExternal,
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("save_as_file_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE5A038))
            ) {
                Icon(
                    imageVector = Icons.Default.SaveAs,
                    contentDescription = "Save As",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save As...", fontSize = 11.sp, maxLines = 1)
            }

            // Open from folder
            OutlinedButton(
                onClick = onOpenExternal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_file_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3FB950))
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Open",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open", fontSize = 11.sp, maxLines = 1)
            }
        }

        HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Files & Templates List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "WORKSPACE FILES (${files.size})",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(files, key = { it.id }) { file ->
                val isSelected = file.id == currentFileId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectFile(file) }
                        .testTag("file_item_${file.name}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF0D2D44) else Color(0xFF252526)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Java File",
                                tint = if (isSelected) Color(0xFF58A6FF) else Color(0xFFE5A038),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = file.name,
                                    color = if (isSelected) Color(0xFF58A6FF) else Color(0xFFE6EDF3),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (file.uriString != null) {
                                    Text(
                                        text = "External Folder Link",
                                        color = Color(0xFF3FB950),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        if (files.size > 1) {
                            IconButton(
                                onClick = { onDeleteFile(file.id) },
                                modifier = Modifier.size(28.dp).testTag("delete_file_${file.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete File",
                                    tint = Color(0xFF6E7681),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = "Templates",
                        tint = Color(0xFFD29922),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BUILT-IN TEMPLATES & SAMPLES",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(templates, key = { "tmpl_${it.id}" }) { tmpl ->
                Surface(
                    color = Color(0xFF161B22),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLoadTemplate(tmpl) }
                        .testTag("template_${tmpl.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tmpl.name,
                            color = Color(0xFFC9D1D9),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Load",
                            color = Color(0xFF58A6FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

}
