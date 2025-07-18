package de.westnordost.streetcomplete.quests.building_type

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BUILDING
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.building.BuildingType
import de.westnordost.streetcomplete.osm.building.INVALID_BUILDING_TYPES
import de.westnordost.streetcomplete.osm.building.OTHER_KEYS_POTENTIALLY_DESCRIBING_BUILDING_TYPE
import de.westnordost.streetcomplete.osm.building.applyTo

class AddBuildingType : OsmFilterQuestType<BuildingType>(), AndroidQuest {

    override val elementFilter = """
        ways, relations with
        building ~ yes|${INVALID_BUILDING_TYPES.joinToString("|")}
        and ${OTHER_KEYS_POTENTIALLY_DESCRIBING_BUILDING_TYPE.joinToString(" and ") { "!$it" }}
        and location != underground
        and disused != yes
        and abandoned != yes
        and ruins != yes
    """
    override val changesetComment = "Specify building types"
    override val wikiLink = "Key:building"
    override val icon = R.drawable.ic_quest_building
    override val achievements = listOf(BUILDING)
    override val defaultDisabledMessage = R.string.default_disabled_msg_overlay

    override fun getTitle(tags: Map<String, String>) = R.string.quest_buildingType_title

    override fun createForm() = AddBuildingTypeForm()

    override fun applyAnswerTo(answer: BuildingType, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        answer.applyTo(tags)
    }
}
