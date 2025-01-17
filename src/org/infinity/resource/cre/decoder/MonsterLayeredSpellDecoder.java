// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2022 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre.decoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.cre.decoder.tables.SpriteTables;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.cre.decoder.util.DecoderAttribute;
import org.infinity.resource.cre.decoder.util.DirDef;
import org.infinity.resource.cre.decoder.util.ItemInfo;
import org.infinity.resource.cre.decoder.util.SegmentDef;
import org.infinity.resource.cre.decoder.util.SeqDef;
import org.infinity.resource.cre.decoder.util.Sequence;
import org.infinity.resource.cre.decoder.util.SpriteUtils;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapSection;
import org.infinity.util.tuples.Couple;

/**
 * Creature animation decoder for processing type 2000 (monster_layered_spell) animations. Note: This type can be
 * incorrectly labeled as "monster_layered" in INI files Available ranges: [2000,2fff]
 */
public class MonsterLayeredSpellDecoder extends SpriteDecoder {
  /** The animation type associated with this class definition. */
  public static final AnimationInfo.Type ANIMATION_TYPE = AnimationInfo.Type.MONSTER_LAYERED_SPELL;

  public static final DecoderAttribute KEY_DUAL_ATTACK = DecoderAttribute.with("dual_attack",
      DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_INVULNERABLE = DecoderAttribute.with("invulnerable",
      DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_RESREF_WEAPON1 = DecoderAttribute.with("resref_weapon1",
      DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_RESREF_WEAPON2 = DecoderAttribute.with("resref_weapon2",
      DecoderAttribute.DataType.STRING);

  private static final HashMap<Sequence, Couple<String, Integer>> SUFFIX_MAP = new HashMap<Sequence, Couple<String, Integer>>();

  static {
    SUFFIX_MAP.put(Sequence.WALK, Couple.with("G1", 0));
    SUFFIX_MAP.put(Sequence.STANCE, Couple.with("G1", 8));
    SUFFIX_MAP.put(Sequence.STAND, Couple.with("G1", 16));
    SUFFIX_MAP.put(Sequence.GET_HIT, Couple.with("G1", 24));
    SUFFIX_MAP.put(Sequence.DIE, Couple.with("G1", 32));
    SUFFIX_MAP.put(Sequence.SLEEP, SUFFIX_MAP.get(Sequence.DIE));
    SUFFIX_MAP.put(Sequence.GET_UP, Couple.with("!G1", 32));
    SUFFIX_MAP.put(Sequence.TWITCH, Couple.with("G1", 40));
    SUFFIX_MAP.put(Sequence.ATTACK, Couple.with("G2", 0));
    SUFFIX_MAP.put(Sequence.SPELL, Couple.with("G2", 8));
    SUFFIX_MAP.put(Sequence.ATTACK_2H, Couple.with("G2", 16));
  }

  /**
   * A helper method that parses the specified data array and generates a {@link IniMap} instance out of it.
   *
   * @param data a String array containing table values for a specific table entry.
   * @return a {@code IniMap} instance with the value derived from the specified data array. Returns {@code null} if no
   *         data could be derived.
   */
  public static IniMap processTableData(String[] data) {
    IniMap retVal = null;
    if (data == null || data.length < 16) {
      return retVal;
    }

    String resref = SpriteTables.valueToString(data, SpriteTables.COLUMN_RESREF, "");
    if (resref.isEmpty()) {
      return retVal;
    }
    int falseColor = SpriteTables.valueToInt(data, SpriteTables.COLUMN_CLOWN, 0);
    int dualAttack = SpriteTables.valueToInt(data, SpriteTables.COLUMN_WEAPON, 0);
    String resrefWeapon1 = SpriteTables.valueToString(data, SpriteTables.COLUMN_HEIGHT, "");
    String resrefWeapon2 = SpriteTables.valueToString(data, SpriteTables.COLUMN_HEIGHT_SHIELD, "");

    List<String> lines = SpriteUtils.processTableDataGeneral(data, ANIMATION_TYPE);
    lines.add("[monster_layered_spell]");
    lines.add("dual_attack=" + dualAttack);
    lines.add("false_color=" + falseColor);
    lines.add("resref=" + resref);
    lines.add("resref_weapon1=" + resrefWeapon1);
    lines.add("resref_weapon2=" + resrefWeapon2);

    retVal = IniMap.from(lines);

    return retVal;
  }

  public MonsterLayeredSpellDecoder(int animationId, IniMap ini) throws Exception {
    super(ANIMATION_TYPE, animationId, ini);
  }

  public MonsterLayeredSpellDecoder(CreResource cre) throws Exception {
    super(ANIMATION_TYPE, cre);
  }

  /** ??? */
  public boolean isDualAttack() {
    return getAttribute(KEY_DUAL_ATTACK);
  }

  protected void setDualAttack(boolean b) {
    setAttribute(KEY_DUAL_ATTACK, b);
  }

  /** Returns the two-letter weapon animation prefix for 1-handed weapons. */
  public String getWeapon1Overlay() {
    return getAttribute(KEY_RESREF_WEAPON1);
  }

  protected void setWeapon1Overlay(String s) {
    setAttribute(KEY_RESREF_WEAPON1, s);
  }

  /** Returns the two-letter weapon animation prefix for 2-handed weapons. */
  public String getWeapon2Overlay() {
    return getAttribute(KEY_RESREF_WEAPON2);
  }

  protected void setWeapon2Overlay(String s) {
    setAttribute(KEY_RESREF_WEAPON2, s);
  }

  @Override
  public List<String> getAnimationFiles(boolean essential) {
    String resref = getAnimationResref();
    final String w1 = !getWeapon1Overlay().isEmpty() ? getWeapon1Overlay().substring(0, 1) : "";
    final String w2 = !getWeapon2Overlay().isEmpty() ? getWeapon2Overlay().substring(0, 1) : "";
    final String[] suffix = { "G1", "G1E", "G2", "G2E" };
    ArrayList<String> retVal = new ArrayList<String>() {
      {
        for (final String s : suffix) {
          add(resref + s + ".BAM");
        }
        if (!w1.isEmpty()) {
          for (final String s : suffix) {
            add(resref + w1 + s + ".BAM");
          }
        }
        if (!w2.isEmpty()) {
          for (final String s : suffix) {
            add(resref + w2 + s + ".BAM");
          }
        }
      }
    };
    return retVal;
  }

  @Override
  public boolean isSequenceAvailable(Sequence seq) {
    return (getSequenceDefinition(seq) != null);
  }

  @Override
  protected void init() throws Exception {
    // setting properties
    initDefaults(getAnimationInfo());

    // Hardcoded defaults
    int defFalseColor = 0;
    int defDualAttack = 0;
    String defWeapon1 = "";
    String defWeapon2 = "";
    // switch (getAnimationId() & 0xff00) {
    // case 0x2000: // Sirine
    // defFalseColor = 1;
    // defWeapon2 = "BW";
    // break;
    // case 0x2100: // Volo
    // defWeapon1 = "MS";
    // break;
    // case 0x2200: // Ogre Mage
    // defFalseColor = 1;
    // defDualAttack = 1;
    // defWeapon1 = "S1";
    // break;
    // case 0x2300: // Death Knight
    // defDualAttack = 1;
    // break;
    // }

    IniMapSection section = getSpecificIniSection();
    if (section.getEntryCount() == 0) {
      // EE: defined as "monster_layered" type
      section = getAnimationInfo().getSection(AnimationInfo.Type.MONSTER_LAYERED.getSectionName());
    }

    setDetectedByInfravision(true);
    setFalseColor(section.getAsInteger(KEY_FALSE_COLOR.getName(), defFalseColor) != 0);
    setDualAttack(section.getAsInteger(KEY_DUAL_ATTACK.getName(), defDualAttack) != 0);
    setWeapon1Overlay(section.getAsString(KEY_RESREF_WEAPON1.getName(), defWeapon1));
    setWeapon2Overlay(section.getAsString(KEY_RESREF_WEAPON2.getName(), defWeapon2));
  }

  @Override
  protected SeqDef getSequenceDefinition(Sequence seq) {
    SeqDef retVal = null;

    Couple<String, Integer> data = SUFFIX_MAP.get(seq);
    if (data == null) {
      return retVal;
    }

    ItemInfo itmWeapon = getCreatureInfo().getEquippedWeapon();
    if (seq == Sequence.ATTACK_2H && ItemInfo.testAll(itmWeapon, ItemInfo.FILTER_WEAPON_2H)) {
      return retVal;
    }

    ArrayList<Couple<String, SegmentDef.SpriteType>> creResList = new ArrayList<>();

    // defining creature resref prefix
    String resref = getAnimationResref();
    SegmentDef.Behavior behavior = SegmentDef.getBehaviorOf(data.getValue0());
    String suffix = SegmentDef.fixBehaviorSuffix(data.getValue0());
    creResList.add(Couple.with(resref + suffix, SegmentDef.SpriteType.AVATAR));

    // defining weapon overlay for current creature
    if (itmWeapon != null) {
      String weapon = itmWeapon.getAppearance().trim();
      if (!weapon.isEmpty()) {
        weapon = weapon.substring(0, 1);
      }
      if (!getWeapon1Overlay().startsWith(weapon) && !getWeapon2Overlay().startsWith(weapon)) {
        weapon = "";
      }

      if (!weapon.isEmpty()) {
        creResList.add(Couple.with(resref + weapon + suffix, SegmentDef.SpriteType.WEAPON));
      }
    }

    int cycle = data.getValue1();
    int cycleE = cycle + SeqDef.DIR_REDUCED_W.length;

    retVal = new SeqDef(seq);
    for (Couple<String, SegmentDef.SpriteType> resEntry : creResList) {
      ResourceEntry entry = ResourceFactory.getResourceEntry(resEntry.getValue0() + ".BAM");
      ResourceEntry entryE = ResourceFactory.getResourceEntry(resEntry.getValue0() + "E.BAM");
      if (SpriteUtils.bamCyclesExist(entry, cycle, SeqDef.DIR_REDUCED_W.length)
          && SpriteUtils.bamCyclesExist(entryE, cycleE, SeqDef.DIR_REDUCED_E.length)) {
        SeqDef tmp = SeqDef.createSequence(seq, SeqDef.DIR_REDUCED_W, false, entry, cycle, resEntry.getValue1(),
            behavior);
        retVal.addDirections(tmp.getDirections().toArray(new DirDef[tmp.getDirections().size()]));
        tmp = SeqDef.createSequence(seq, SeqDef.DIR_REDUCED_E, false, entryE, cycleE, resEntry.getValue1(), behavior);
        retVal.addDirections(tmp.getDirections().toArray(new DirDef[tmp.getDirections().size()]));
      }
    }

    if (retVal.isEmpty()) {
      retVal = null;
    }

    return retVal;
  }

}
