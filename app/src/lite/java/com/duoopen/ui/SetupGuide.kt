package com.duoopen.ui

import androidx.compose.runtime.Composable

/** Lite edition has no accessibility service, so there is nothing to guide through. */
@Composable
fun SetupGuide(onOpenAccessibility: () -> Unit, onDismiss: () -> Unit) = Unit
