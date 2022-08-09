package net.liplum.ui

import arc.Core
import arc.Events
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Dialog
import arc.scene.ui.TextButton
import arc.scene.ui.layout.Table
import arc.util.Align
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.core.GameState.State.menu
import mindustry.game.EventType
import mindustry.game.EventType.Trigger
import mindustry.gen.Sounds
import mindustry.ui.Styles
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.CheckSetting
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.SliderSetting
import net.liplum.*
import net.liplum.Settings.percentage2Density
import net.liplum.annotations.Only
import net.liplum.annotations.SubscribeEvent
import net.liplum.common.ing
import net.liplum.common.util.bundle
import net.liplum.common.util.getF
import net.liplum.common.util.randomExcept
import net.liplum.event.CioInitEvent
import plumy.core.UseReflection
import net.liplum.mdt.ClientOnly
import net.liplum.mdt.IsLocal
import net.liplum.mdt.UnsteamOnly
import net.liplum.mdt.advanced.MapCleaner
import net.liplum.mdt.ui.MainMenus
import net.liplum.mdt.ui.ShowTextDialog
import net.liplum.mdt.ui.addTrackTooltip
import net.liplum.mdt.ui.settings.*
import net.liplum.mdt.ui.settings.AnySetting.Companion.addAny
import net.liplum.mdt.ui.settings.CheckSettingX.Companion.addCheckPref
import net.liplum.mdt.ui.settings.SliderSettingX.Companion.addSliderSettingX
import net.liplum.update.Updater
import net.liplum.welcome.Conditions
import net.liplum.welcome.Welcome
import net.liplum.welcome.WelcomeList

@ClientOnly
object CioUI {
    val textIcon by lazy {
        TextureRegionDrawable("icon-text".cioTR)
    }
    @JvmStatic
    @SubscribeEvent(CioInitEvent::class, Only.client)
    fun appendUI() {
        addCyberIOSettingMenu()
        addCyberIOMenu()
    }
    @UseReflection
    fun addCyberIOSettingMenu() {
        val uiSettings = Vars.ui.settings
        val menu = uiSettings.getF<Table>("menu")
        val prefs = uiSettings.getF<Table>("prefs")
        uiSettings.resized {
            settings.rebuild()
            uiSettings.updateScrollFocus()
        }
        val marg = 8f
        Events.run(Trigger.update) {
            if (Vars.ui.settings.isShown) {
                val cioSettings = menu.find<TextButton>(SettingButtonName)
                if (cioSettings == null) {
                    menu.row()
                    menu.button(
                        Meta.Name,
                        textIcon,
                        Styles.flatt, Vars.iconMed
                    ) {
                        prefs.clearChildren()
                        prefs.add(settings)
                    }.marginLeft(marg).get().apply {
                        name = SettingButtonName
                    }
                }
            }
        }
    }
    @UseReflection
    fun addCyberIOMenu() {
        val cioMenuID = "cyber-io-menu"
        if (!Vars.mobile) {
            MainMenus.appendDesktopMenu("Cyber IO", textIcon, cioMenuID) {
                CyberIOMenu.show()
            }
        } else {
            MainMenus.appendMobileMenu("Cyber IO", textIcon, cioMenuID) {
                CyberIOMenu.show()
            }
        }
    }

    const val SettingButtonName = "cyber-io-settings-button"
    val settings = SettingsTableX().apply {
        var isMenu = true
        genHeader = {
            it.add("[#${Var.ContentSpecific.color}]${Meta.Name} v${Meta.DetailedVersion} ${Var.ContentSpecific.i18nName}[]").row()
        }
        addSliderSettingX(R.Setting.LinkOpacity,
            100, 0, 100, 5, { "$it%" }
        ) {
            Settings.LinkOpacity = Core.settings.getInt(R.Setting.LinkOpacity) / 100f
        }

        addSliderSettingX(R.Setting.LinkArrowDensity,
            15, 0, 100, 5, { "$it" }
        ) {
            Settings.LinkArrowDensity = percentage2Density(Core.settings.getInt(R.Setting.LinkArrowDensity, 15))
        }
        addSliderSettingX(R.Setting.LinkAnimationSpeed,
            40, 0, 120, 5, { "$it" }
        ) {
            Settings.LinkArrowSpeed = Core.settings.getInt(R.Setting.LinkAnimationSpeed, 40).toFloat()
        }
        addCheckPref(R.Setting.AlwaysShowLink, true) {
            Settings.AlwaysShowLink = Core.settings.getBool(R.Setting.AlwaysShowLink, true)
        }
        addCheckPref(R.Setting.LinkBloom, true) {
            Settings.LinkBloom = Core.settings.getBool(R.Setting.LinkBloom, true)
        }
        addCheckPref(R.Setting.ShowLinkCircle, false) {
            Settings.ShowLinkCircle = Core.settings.getBool(R.Setting.ShowLinkCircle, false)
        }
        addCheckPref(R.Setting.ShowWirelessTowerCircle, true) {
            Settings.ShowWirelessTowerCircle = Core.settings.getBool(R.Setting.ShowWirelessTowerCircle, true)
        }
        UnsteamOnly {
            addCheckPref(
                R.Setting.ShowUpdate, !Vars.steam
            ) {
                Settings.ShowUpdate = it
            }.apply {
                canShow = { isMenu }
            }
        }
        addCheckPref(
            R.Setting.ShowWelcome, true
        ) {
            Settings.ShouldShowWelcome = it
        }.apply {
            canShow = { isMenu }
        }
        // GitHub mirror and Check update
        UnsteamOnly {
            // Check Update
            addAny {
                fun bundle(key: String) = "setting.${R.Setting.CheckUpdate}.$key".bundle
                val buttonI18n = bundle("button")
                val button = TextButton(buttonI18n).apply {
                    update {
                        label.setText(if (isDisabled) buttonI18n.ing else buttonI18n)
                    }
                    changed {
                        var failed: String? = null
                        Updater.fetchLatestVersion(onFailed = { e ->
                            failed = e
                        })
                        isDisabled = true
                        Updater.launch {
                            Updater.accessJob?.join()
                            val errorInfo = failed
                            if (errorInfo != null) {
                                showUpdateFailed(errorInfo)
                            } else {
                                if (Updater.requireUpdate) {
                                    val updateTips = WelcomeList.findAll { tip ->
                                        tip.condition == Conditions.CheckUpdate
                                    }
                                    if (updateTips.isEmpty()) {
                                        ShowTextDialog(bundle("not-support"))
                                    } else {
                                        val updateTip = updateTips.randomExcept(atLeastOne = true) {
                                            id != Settings.LastWelcomeID
                                        }
                                        if (updateTip != null)
                                            Welcome.genEntity().apply {
                                                tip = updateTip
                                            }.showTip()
                                        else
                                            ShowTextDialog(bundle("not-support"))
                                    }
                                } else {
                                    ShowTextDialog(bundle("already-latest"))
                                }
                            }
                            isDisabled = false
                        }
                    }
                }.addTrackTooltip(bundle("button-tooltip")).apply {
                    canShow = { isMenu }
                }
                it.add(button).fillX()
            }
        }
        addAny {
            it.add(TextButton(AdvancedFunctionDialog.bundle("title")).apply {
                clicked {
                    AdvancedFunctionDialog.show(onReset)
                }
            }).fillX()
        }.apply {
            canShow = { isMenu }
        }
        addAny {
            it.add(TextButton(R.Advanced.MapCleaner.bundle).apply {
                clicked {
                    MapCleaner.cleanCurrentMap(Meta.ModID)
                    Sounds.message.play()
                }
                update {
                    isDisabled = !(Var.EnableMapCleaner && !isMenu && IsLocal())
                }
            }).fillX()
        }.apply {
            canShow = { Var.EnableMapCleaner && !isMenu && IsLocal() }
        }
        DebugOnly {
            addAny {
                val button = TextButton("Debug Settings").apply {
                    changed {
                        DebugSettingsDialog.show()
                    }
                }.addTrackTooltip("Only for debugging.")
                it.add(button).fillX()
            }
        }
        sortBy {
            when (it) {
                is SliderSetting -> 0
                is SliderSettingX -> 1
                is CheckSetting -> 2
                is CheckSettingX -> 3
                is AnySetting -> 4
                else -> Int.MAX_VALUE
            }
        }
        Events.on(EventType.StateChangeEvent::class.java) {
            val lastIsMenu = isMenu
            isMenu = it.to == menu
            if (lastIsMenu != isMenu) {
                rebuild()
            }
        }
    }

    fun showUpdateFailed(error: String) {
        Dialog().apply {
            getCell(cont).growX()
            cont.margin(15f).add(
                R.Ctrl.UpdateModFailed.bundle(error)
            ).width(400f).wrap().get().setAlignment(Align.center, Align.center)
            buttons.button("@ok") {
                this.hide()
            }.size(110f, 50f).pad(4f)
            closeOnBack()
        }.show()
    }
}