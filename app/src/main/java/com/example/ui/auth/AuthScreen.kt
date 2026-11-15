package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isCheckingSession by remember { mutableStateOf(true) }
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingSession = false
        onAuthSuccess()
      },
      onUnauthenticated = {
        isCheckingSession = false
      },
      scope = coroutineScope,
    )
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          tonalElevation = 1.dp,
        ) {
          Image(
            painter = painterResource(id = com.example.R.drawable.img_editorial_crest_1794675713083),
            contentDescription = stringResource(com.example.R.string.editorial_crest_desc),
            modifier = Modifier
              .fillMaxWidth()
              .height(190.dp)
              .clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Crop,
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = stringResource(com.example.R.string.app_name),
          style = MaterialTheme.typography.displayLarge,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = stringResource(com.example.R.string.auth_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            EditorialFeatureRow(
              icon = Icons.Outlined.CalendarMonth,
              title = stringResource(com.example.R.string.auth_feature_schedule_title),
              subtitle = stringResource(com.example.R.string.auth_feature_schedule_desc),
            )
            HorizontalDivider(
              modifier = Modifier.padding(vertical = 12.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
            EditorialFeatureRow(
              icon = Icons.Outlined.Groups,
              title = stringResource(com.example.R.string.auth_feature_team_title),
              subtitle = stringResource(com.example.R.string.auth_feature_team_desc),
            )
            HorizontalDivider(
              modifier = Modifier.padding(vertical = 12.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
            EditorialFeatureRow(
              icon = Icons.Outlined.AutoStories,
              title = stringResource(com.example.R.string.auth_feature_visibility_title),
              subtitle = stringResource(com.example.R.string.auth_feature_visibility_desc),
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isSigningIn = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isSigningIn = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isSigningIn = false
                errorMessage = msg
              },
              scope = coroutineScope,
              onAuthCancelled = {
                isSigningIn = false
              },
            )
          },
          enabled = !isSigningIn && !isCheckingSession,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("google_sign_in_button"),
          shape = MaterialTheme.shapes.medium,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        ) {
          if (isSigningIn || isCheckingSession) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = if (isCheckingSession) "Checking session…" else "Signing in…",
              style = MaterialTheme.typography.labelLarge,
            )
          } else {
            Icon(
              imageVector = Icons.Outlined.Login,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = stringResource(com.example.R.string.sign_in_with_google),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = stringResource(com.example.R.string.auth_first_user_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun EditorialFeatureRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(38.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(8.dp),
      )
    }
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
