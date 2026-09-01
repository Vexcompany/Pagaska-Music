@@
-                val showChangelog = com.metrolist.music.LocalChangelogState.current
-                add(
-                    Material3SettingsItem(
-                        icon = painterResource(R.drawable.newspaper),
-                        title = { Text(stringResource(R.string.changelog)) },
-                        onClick = { showChangelog.value = true }
-                    )
-                )
+                // Replace Changelog with Pagaska Social
+                val showChangelog = com.metrolist.music.LocalChangelogState.current
+                add(
+                    Material3SettingsItem(
+                        icon = painterResource(R.drawable.link),
+                        title = { Text(stringResource(R.string.pagaska_social)) },
+                        onClick = { showChangelog.value = true }
+                    )
+                )
*** End Patch
