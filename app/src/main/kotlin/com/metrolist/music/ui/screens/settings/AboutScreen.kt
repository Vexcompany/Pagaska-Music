/**
 * Pagaska Music branding and credits.
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem

private const val PAGASKA_INSTAGRAM = "https://www.instagram.com/pagaska_"
private const val PAGASKA_TIKTOK = "https://www.tiktok.com/@gala.taksaka"
private const val PAGASKA_YOUTUBE = "https://youtube.com/@pagaskasmkn5madiun?si=gYoUXbunjl-dswDr"
private const val VEX_INSTAGRAM = "https://www.instagram.com/whoisvex"
private const val VEX_TIKTOK = "https://www.tiktok.com/@whoisvex._"
private const val TRAKTEER = "https://trakteer.id/verolyz"
private const val SAWERIA = "https://saweria.co/Vex001"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
) {
    val uriHandler = LocalUriHandler.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(16.dp))

        ElevatedCard(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.about_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(84.dp)
                )
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        text = "Pagaska Music",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = BuildConfig.ARCHITECTURE.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        ElevatedCard(
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Vex",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.credits_lead_developer),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(VEX_INSTAGRAM) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(painterResource(R.drawable.instagram), contentDescription = "Instagram")
                    }
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(VEX_TIKTOK) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(painterResource(R.drawable.music_note), contentDescription = "TikTok")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { uriHandler.openUri(TRAKTEER) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Trakteer", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { uriHandler.openUri(SAWERIA) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Saweria", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Material3SettingsGroup(
            title = "Contributors",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.group),
                    title = { Text("Pagaska", fontWeight = FontWeight.SemiBold) },
                    description = { Text("Contributor") }
                )
            )
        )

        Spacer(Modifier.height(32.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.community_and_info),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.instagram),
                    title = { Text("Instagram", fontWeight = FontWeight.SemiBold) },
                    description = { Text("Pagaska") },
                    onClick = { uriHandler.openUri(PAGASKA_INSTAGRAM) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("TikTok", fontWeight = FontWeight.SemiBold) },
                    description = { Text("Pagaska") },
                    onClick = { uriHandler.openUri(PAGASKA_TIKTOK) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text("YouTube", fontWeight = FontWeight.SemiBold) },
                    description = { Text("Pagaska") },
                    onClick = { uriHandler.openUri(PAGASKA_YOUTUBE) }
                )
            )
        )

        Spacer(Modifier.height(32.dp))

        Material3SettingsGroup(
            title = "Contribute",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.instagram),
                    title = { Text("Instagram", fontWeight = FontWeight.SemiBold) },
                    description = { Text("Pagaska") },
                    onClick = { uriHandler.openUri(PAGASKA_INSTAGRAM) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text("TikTok", fontWeight = FontWeight.SemiBold) },
                    description = { Text("Pagaska") },
                    onClick = { uriHandler.openUri(PAGASKA_TIKTOK) }
                )
            )
        )

        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.stands_with_palestine),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
