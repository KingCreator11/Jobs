/**
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011 Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gamingmesh.jobs.container;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.CMILib.ActionBarManager;
import com.gamingmesh.jobs.CMILib.CMIChatColor;
import com.gamingmesh.jobs.CMILib.CMIMaterial;
import com.gamingmesh.jobs.Signs.SignTopType;
import com.gamingmesh.jobs.container.blockOwnerShip.BlockTypes;
import com.gamingmesh.jobs.dao.JobsDAO;
import com.gamingmesh.jobs.economy.PaymentData;
import com.gamingmesh.jobs.resources.jfep.Parser;
import com.gamingmesh.jobs.stuff.TimeManage;

public class JobsPlayer {

    private String userName = "Unknown";

    public UUID playerUUID;

    // progression of the player in each job
    public final ArrayList<JobProgression> progression = new ArrayList<>();

    public int maxJobsEquation = 0;

    private ArchivedJobs archivedJobs = new ArchivedJobs();
    private PaymentData paymentLimits;

    private final HashMap<String, ArrayList<BoostCounter>> boostCounter = new HashMap<>();

    // display honorific
    private String honorific;
    // player save status
    private volatile boolean isSaved = true;
    // player online status
    private volatile boolean isOnline = false;

    private final HashMap<CurrencyType, Integer> limits = new HashMap<>();

    private int userid = -1;

    private final List<BossBarInfo> barMap = new ArrayList<>();
    private final List<String> updateBossBarFor = new ArrayList<>();
    // save lock
//    public final Object saveLock = new Object();

    private HashMap<String, Log> logList = new HashMap<>();

    private Long seen = System.currentTimeMillis();

    private HashMap<String, Boolean> permissionsCache;
    private Long lastPermissionUpdate = -1L;

    private final HashMap<String, HashMap<String, QuestProgression>> qProgression = new HashMap<>();
    private int doneQuests = 0;
    private int skippedQuests = 0;

    private final HashMap<UUID, HashMap<Job, Long>> leftTimes = new HashMap<>();

    private PlayerPoints pointsData;

    public JobsPlayer(String userName) {
	this.userName = userName == null ? "Unknown" : userName;
    }

    /**
     * @return the cached or new instance of {@link PlayerPoints}
     */
    public PlayerPoints getPointsData() {
	if (pointsData == null)
	    pointsData = new PlayerPoints();
	return pointsData;
    }

    /**
     * Adds points to this player.
     * 
     * @param points the amount of points
     */
    public void addPoints(double points) {
	getPointsData().addPoints(points);
    }

    /**
     * Takes points from this player.
     * 
     * @param points the amount of points
     */
    public void takePoints(double points) {
	getPointsData().takePoints(points);
    }

    /**
     * Sets points for this player.
     * 
     * @param points the amount of points
     */
    public void setPoints(double points) {
	getPointsData().setPoints(points);
    }

    /**
     * Sets points for this player from the given {@link PlayerPoints}
     * 
     * @param points {@link PlayerPoints}
     */
    public void setPoints(PlayerPoints points) {
	getPointsData().setPoints(points.getCurrentPoints());
	getPointsData().setTotalPoints(points.getTotalPoints());
	getPointsData().setDbId(points.getDbId());
    }

    /**
     * Checks whenever have enough points for the given one.
     * 
     * @param points amount of points
     * @return true if yes
     */
    public boolean havePoints(double points) {
	return getPointsData().getCurrentPoints() >= points;
    }

    /**
     * @return the cached instance of {@link ArchivedJobs}
     */
    public ArchivedJobs getArchivedJobs() {
	return archivedJobs;
    }

    /**
     * Returns the given archived job progression.
     * 
     * @param job {@link Job}
     * @return the given archived job progression
     */
    public JobProgression getArchivedJobProgression(Job job) {
	return archivedJobs.getArchivedJobProgression(job);
    }

    public void setArchivedJobs(ArchivedJobs archivedJob) {
	this.archivedJobs = archivedJob;
    }

    /**
     * @return the total level of all jobs for this player
     */
    public int getTotalLevels() {
	int i = 0;
	for (JobProgression job : progression) {
	    i += job.getLevel();
	}
	return i;
    }

    public void setPaymentLimit(PaymentData paymentLimits) {
	this.paymentLimits = paymentLimits;
    }

    /**
     * @return the limit of {@link PaymentData}
     */
    public PaymentData getPaymentLimit() {
	if (paymentLimits == null)
	    paymentLimits = Jobs.getJobsDAO().getPlayersLimits(this);

	if (paymentLimits == null)
	    paymentLimits = new PaymentData();
	return paymentLimits;
    }

    /**
     * Checks whenever this player is under limit for specific {@link CurrencyType}
     * 
     * @param type {@link CurrencyType}
     * @param amount amount of points
     * @return true if it is under
     */
    public boolean isUnderLimit(CurrencyType type, Double amount) {
	Player player = getPlayer();
	if (player == null || amount == 0)
	    return true;
	CurrencyLimit limit = Jobs.getGCManager().getLimit(type);
	if (!limit.isEnabled())
	    return true;
	PaymentData data = getPaymentLimit();
	Integer value = limits.getOrDefault(type, 0);
	if (data.isReachedLimit(type, value)) {
	    String name = type.getName().toLowerCase();

	    if (player.isOnline() && !data.isInformed() && !data.isReseted(type)) {
		if (Jobs.getGCManager().useMaxPaymentCurve) {
		    player.sendMessage(Jobs.getLanguage().getMessage("command.limit.output.reached" + name + "limit"));
		    player.sendMessage(Jobs.getLanguage().getMessage("command.limit.output.reached" + name + "limit2"));
		    player.sendMessage(Jobs.getLanguage().getMessage("command.limit.output.reached" + name + "limit3"));
		} else {
		    player.sendMessage(Jobs.getLanguage().getMessage("command.limit.output.reached" + name + "limit"));
		    player.sendMessage(Jobs.getLanguage().getMessage("command.limit.output.reached" + name + "limit2"));
		}
		data.setInformed(true);
	    }
	    if (data.isAnnounceTime(limit.getAnnouncementDelay()) && player.isOnline())
		ActionBarManager.send(player, Jobs.getLanguage().getMessage("command.limit.output." + name + "time", "%time%", TimeManage.to24hourShort(data.getLeftTime(type))));
	    if (data.isReseted(type))
		data.setReseted(type, false);
	    return false;
	}
	data.addAmount(type, amount);
	return true;
    }

    public double percentOverLimit(CurrencyType type) {
	Integer value = limits.get(type);
	return getPaymentLimit().percentOverLimit(type, value == null ? 0 : value);
    }

    /**
     * Attempt to load log for this player.
     * 
     * @deprecated use {@link JobsDAO#loadLog(JobsPlayer)} instead
     */
    @Deprecated
    public void loadLogFromDao() {
	Jobs.getJobsDAO().loadLog(this);
    }

    public List<String> getUpdateBossBarFor() {
	return updateBossBarFor;
    }

    @Deprecated
    public void clearUpdateBossBarFor() {
	updateBossBarFor.clear();
    }

    public List<BossBarInfo> getBossBarInfo() {
	return barMap;
    }

    public void hideBossBars() {
	for (BossBarInfo one : barMap) {
	    one.getBar().setVisible(false);
	}
    }

    public HashMap<String, Log> getLog() {
	return logList;
    }

    public void setLog(HashMap<String, Log> logList) {
	this.logList = logList;
    }

    public void setUserId(int userid) {
	this.userid = userid;
    }

    public int getUserId() {
	return userid;
    }

    /**
     * @return {@link Player} or null if not exist
     */
    public Player getPlayer() {
	return playerUUID != null ? Bukkit.getPlayer(playerUUID) : null;
    }

    /**
     * Attempts to get the boost from specific job and {@link CurrencyType}
     * 
     * @param JobName
     * @param type {@link CurrencyType}
     * @see #getBoost(String, CurrencyType, boolean)
     * @return amount of boost
     */
    public double getBoost(String JobName, CurrencyType type) {
	return getBoost(JobName, type, false);
    }

    /**
     * Attempts to get the boost from specific job and {@link CurrencyType}
     * 
     * @param JobName
     * @param type {@link CurrencyType}
     * @param force whenever to retrieve as soon as possible without time
     * @return amount of boost
     */
    public double getBoost(String JobName, CurrencyType type, boolean force) {
	double Boost = 0D;

	if (!isOnline() || type == null)
	    return Boost;

	long time = System.currentTimeMillis();

	if (boostCounter.containsKey(JobName)) {
	    ArrayList<BoostCounter> counterList = boostCounter.get(JobName);
	    for (BoostCounter counter : counterList) {
		if (counter.getType() != type)
		    continue;

		if (force || time - counter.getTime() > 1000 * 60) {
		    Boost = getPlayerBoostNew(JobName, type);
		    counter.setBoost(Boost);
		    counter.setTime(time);
		    return Boost;
		}

		return counter.getBoost();
	    }

	    Boost = getPlayerBoostNew(JobName, type);
	    counterList.add(new BoostCounter(type, Boost, time));
	    return Boost;
	}

	Boost = getPlayerBoostNew(JobName, type);

	ArrayList<BoostCounter> counterList = new ArrayList<>();
	counterList.add(new BoostCounter(type, Boost, time));

	boostCounter.put(JobName, counterList);
	return Boost;
    }

    private Double getPlayerBoostNew(String JobName, CurrencyType type) {
	Double v1 = Jobs.getPermissionManager().getMaxPermission(this, "jobs.boost." + JobName + "." + type.getName(), true, false);
	Double Boost = v1;

	v1 = Jobs.getPermissionManager().getMaxPermission(this, "jobs.boost." + JobName + ".all", false, false);
	if (v1 != 0d && (v1 > Boost || v1 < Boost))
	    Boost = v1;

	v1 = Jobs.getPermissionManager().getMaxPermission(this, "jobs.boost.all.all", false, false);
	if (v1 != 0d && (v1 > Boost || v1 < Boost))
	    Boost = v1;

	v1 = Jobs.getPermissionManager().getMaxPermission(this, "jobs.boost.all." + type.getName(), false, false);
	if (v1 != 0d && (v1 > Boost || v1 < Boost))
	    Boost = v1;

	return Boost;
    }

    public int getPlayerMaxQuest(String jobName) {
	int m1 = Jobs.getPermissionManager().getMaxPermission(this, "jobs.maxquest." + jobName, false, true).intValue();
	int max = m1;

	m1 = Jobs.getPermissionManager().getMaxPermission(this, "jobs.maxquest.all", false, true).intValue();
	if (m1 != 0 && (m1 > max || m1 < max)) {
	    max = m1;
	}

	return max;
    }

    /**
     * Reloads max experience for all jobs for this player.
     */
    public void reloadMaxExperience() {
	progression.forEach(JobProgression::reloadMaxExperience);
    }

    /**
     * Reloads limit for this player.
     */
    public void reload(CurrencyType type) {
	int TotalLevel = getTotalLevels();
	Parser eq = Jobs.getGCManager().getLimit(type).getMaxEquation();
	eq.setVariable("totallevel", TotalLevel);
	maxJobsEquation = Jobs.getPlayerManager().getMaxJobs(this);
	limits.put(type, (int) eq.getValue());
	setSaved(false);
    }

    public void reloadLimits() {
	for (CurrencyType type : CurrencyType.values()) {
	    reload(type);
	}
    }

    public int getLimit(CurrencyType type) {
	return type == null ? 0 : limits.get(type);
    }

    public void resetPaymentLimit() {
	if (paymentLimits == null)
	    getPaymentLimit();
	if (paymentLimits != null)
	    paymentLimits.resetLimits();
	setSaved(false);
    }

    /**
     * @return an unmodifiable list of job progressions
     */
    public List<JobProgression> getJobProgression() {
	return Collections.unmodifiableList(progression);
    }

    /**
     * Get the job progression from the certain job
     * 
     * @param job {@link Job}
     * @return the job progression or null if job not exists
     */
    public JobProgression getJobProgression(Job job) {
	for (JobProgression prog : progression) {
	    if (prog.getJob().isSame(job))
		return prog;
	}
	return null;
    }

    /**
     * get the userName
     * @return the userName
     */
    @Deprecated
    public String getUserName() {
	return getName();
    }

    /**
     * @return the name of this player
     */
    public String getName() {
	Player player = Bukkit.getPlayer(getUniqueId());
	if (player != null)
	    userName = player.getName();
	return userName;
    }

    /**
     * get the playerUUID
     * @return the playerUUID
     */
    @Deprecated
    public UUID getPlayerUUID() {
	return getUniqueId();
    }

    /**
     * @return the {@link UUID} of this player
     */
    public UUID getUniqueId() {
	return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
	setUniqueId(playerUUID);
    }

    public void setUniqueId(UUID playerUUID) {
	this.playerUUID = playerUUID;
    }

    public String getDisplayHonorific() {
	if (honorific == null)
	    reloadHonorific();
	return honorific;
    }

    /**
     * Attempts to join this player to the given job.
     * 
     * @param job where to join
     */
    public boolean joinJob(Job job) {
//	synchronized (saveLock) {
	if (!isInJob(job)) {
	    int level = 1;
	    double exp = 0;

	    JobProgression archived = getArchivedJobProgression(job);
	    if (archived != null) {
		level = getLevelAfterRejoin(archived);
		exp = getExpAfterRejoin(archived, level);
		Jobs.getJobsDAO().deleteArchive(this, job);
	    }

	    progression.add(new JobProgression(job, this, level, exp));
	    reloadMaxExperience();
	    reloadLimits();
	    reloadHonorific();
	    Jobs.getPermissionHandler().recalculatePermissions(this);
	    return true;
	}
	return false;
//	}
    }

    public int getLevelAfterRejoin(JobProgression jp) {
	if (jp == null)
	    return 1;

	int level = jp.getLevel();

	level = (int) ((level - (level * (Jobs.getGCManager().levelLossPercentage / 100.0))));
	if (level < 1)
	    level = 1;

	Job job = jp.getJob();
	int maxLevel = getMaxJobLevelAllowed(job);
	if (jp.getLevel() == maxLevel) {
	    if (Jobs.getGCManager().fixAtMaxLevel)
		level = jp.getLevel();
	    else {
		level = jp.getLevel();
		level = (int) (level - (level * (Jobs.getGCManager().levelLossPercentageFromMax / 100.0)));
		if (level < 1)
		    level = 1;
	    }
	}

	return level;
    }

    public double getExpAfterRejoin(JobProgression jp, int level) {
	if (jp == null)
	    return 1;

	Integer max = jp.getMaxExperience(level);
	Double exp = jp.getExperience();
	if (exp > max)
	    exp = max.doubleValue();

	if (exp > 0) {
	    Job job = jp.getJob();
	    int maxLevel = getMaxJobLevelAllowed(job);
	    if (jp.getLevel() == maxLevel) {
		if (!Jobs.getGCManager().fixAtMaxLevel)
		    exp = (exp - (exp * (Jobs.getGCManager().levelLossPercentageFromMax / 100.0)));
	    } else
		exp = (exp - (exp * (Jobs.getGCManager().levelLossPercentage / 100.0)));
	}

	return exp.doubleValue();
    }

    /**
     * Player leaves a job
     * @param job - the job left
     */
    public boolean leaveJob(Job job) {
//	synchronized (saveLock) {
	JobProgression prog = getJobProgression(job);
	if (prog != null) {
	    progression.remove(prog);
	    reloadMaxExperience();
	    reloadLimits();
	    reloadHonorific();
	    Jobs.getPermissionHandler().recalculatePermissions(this);
	    return true;
	}
	return false;
//	}
    }

    /**
     * Attempts to leave all jobs from this player.
     * @return true if success
     */
    public boolean leaveAllJobs() {
//	synchronized (saveLock) {
	progression.clear();
	reloadHonorific();
	Jobs.getPermissionHandler().recalculatePermissions(this);
	reloadLimits();
	return true;
//	}
    }

    /**
     * Promotes player in job
     * @param job - the job being promoted
     * @param levels - number of levels to promote
     */
    public void promoteJob(Job job, int levels) {
//	synchronized (saveLock) {
	JobProgression prog = getJobProgression(job);
	if (prog == null || levels <= 0)
	    return;

	int oldLevel = prog.getLevel(),
	    newLevel = oldLevel + levels,
	    maxLevel = job.getMaxLevel(this);

	if (maxLevel > 0 && newLevel > maxLevel)
	    newLevel = maxLevel;

	setLevel(job, newLevel);
//	}
    }

    /**
     * Demotes player in job
     * @param job - the job being deomoted
     * @param levels - number of levels to demote
     */
    public void demoteJob(Job job, int levels) {
//	synchronized (saveLock) {
	JobProgression prog = getJobProgression(job);
	if (prog == null || levels <= 0)
	    return;

	int newLevel = prog.getLevel() - levels;
	if (newLevel < 1)
	    newLevel = 1;

	setLevel(job, newLevel);
//	}
    }

    /**
     * Sets player to a specific level
     * @param job - the job
     * @param level - the level
     */
    private void setLevel(Job job, int level) {
//	synchronized (saveLock) {
	JobProgression prog = getJobProgression(job);
	if (prog == null)
	    return;

	if (level != prog.getLevel()) {
	    prog.setLevel(level);
	    reloadHonorific();
	    reloadLimits();
	    Jobs.getPermissionHandler().recalculatePermissions(this);
	}
//	}
    }

    /**
     * Player leaves a job
     * @param oldjob - the old job
     * @param newjob - the new job
     */
    public boolean transferJob(Job oldjob, Job newjob) {
//	synchronized (saveLock) {
	if (!isInJob(newjob)) {
	    for (JobProgression prog : progression) {
		if (!prog.getJob().isSame(oldjob))
		    continue;

		prog.setJob(newjob);

		int maxLevel = getMaxJobLevelAllowed(newjob);

		if (newjob.getMaxLevel() > 0 && prog.getLevel() > maxLevel)
		    prog.setLevel(maxLevel);

		reloadMaxExperience();
		reloadLimits();
		reloadHonorific();
		Jobs.getPermissionHandler().recalculatePermissions(this);
		return true;
	    }
	}
	return false;
//	}
    }

    public int getMaxJobLevelAllowed(Job job) {
	int maxLevel = 0;
	if (getPlayer() != null && (getPlayer().hasPermission("jobs." + job.getName() + ".vipmaxlevel") || getPlayer().hasPermission("jobs.all.vipmaxlevel")))
	    maxLevel = job.getVipMaxLevel() > job.getMaxLevel() ? job.getVipMaxLevel() : job.getMaxLevel();
	else
	    maxLevel = job.getMaxLevel();
	int tMax = Jobs.getPermissionManager().getMaxPermission(this, "jobs." + job.getName() + ".vipmaxlevel").intValue();
	if (tMax > maxLevel)
	    maxLevel = tMax;
	tMax = Jobs.getPermissionManager().getMaxPermission(this, "jobs.all.vipmaxlevel").intValue();
	if (tMax > maxLevel)
	    maxLevel = tMax;
	return maxLevel;
    }

    /**
     * Checks if the player is in the given job.
     * 
     * @param job {@link Job}
     * @return true if this player is in the given job, otherwise false
     */
    public boolean isInJob(Job job) {
	if (job == null)
	    return false;
	for (JobProgression prog : progression) {
	    if (prog.getJob().isSame(job))
		return true;
	}
	return false;
    }

    /**
     * Function that reloads this player honorific
     */
    public void reloadHonorific() {
	StringBuilder builder = new StringBuilder();

	if (progression.size() > 0) {
	    for (JobProgression prog : progression) {
		DisplayMethod method = prog.getJob().getDisplayMethod();
		if (method == DisplayMethod.NONE)
		    continue;
		if (!builder.toString().isEmpty()) {
		    builder.append(Jobs.getGCManager().modifyChatSeparator);
		}
		Title title = Jobs.getTitleManager().getTitle(prog.getLevel(), prog.getJob().getName());
		processesChat(method, builder, prog.getLevel(), title, prog.getJob());
	    }
	} else {
	    Job nonejob = Jobs.getNoneJob();
	    if (nonejob != null) {
		processesChat(nonejob.getDisplayMethod(), builder, -1, null, nonejob);
	    }
	}

	honorific = builder.toString().trim();
	if (!honorific.isEmpty())
	    honorific = CMIChatColor.translate(Jobs.getGCManager().modifyChatPrefix + honorific + Jobs.getGCManager().modifyChatSuffix);
    }

    private static void processesChat(DisplayMethod method, StringBuilder builder, int level, Title title, Job job) {

	String levelS = level < 0 ? "" : String.valueOf(level);
	switch (method) {
	case FULL:
	    processesTitle(builder, title, levelS);
	    processesJob(builder, job, levelS);
	    break;
	case TITLE:
	    processesTitle(builder, title, levelS);
	    break;
	case JOB:
	    processesJob(builder, job, levelS);
	    break;
	case SHORT_FULL:
	    processesShortTitle(builder, title, levelS);
	    processesShortJob(builder, job, levelS);
	    break;
	case SHORT_TITLE:
	    processesShortTitle(builder, title, levelS);
	    break;
	case SHORT_JOB:
	    processesShortJob(builder, job, levelS);
	    break;
	case SHORT_TITLE_JOB:
	    processesShortTitle(builder, title, levelS);
	    processesJob(builder, job, levelS);
	    break;
	case TITLE_SHORT_JOB:
	    processesTitle(builder, title, levelS);
	    processesShortJob(builder, job, levelS);
	    break;
	default:
	    break;
	}
    }

    private static void processesShortTitle(StringBuilder builder, Title title, String levelS) {
	if (title == null)
	    return;
	builder.append(title.getChatColor());
	builder.append(title.getShortName().replace("{level}", levelS));
	builder.append(CMIChatColor.WHITE);
    }

    private static void processesTitle(StringBuilder builder, Title title, String levelS) {
	if (title == null)
	    return;
	builder.append(title.getChatColor());
	builder.append(title.getName().replace("{level}", levelS));
	builder.append(CMIChatColor.WHITE);
    }

    private static void processesShortJob(StringBuilder builder, Job job, String levelS) {
	if (job == null)
	    return;
	builder.append(job.getChatColor());
	builder.append(job.getShortName().replace("{level}", levelS));
	builder.append(CMIChatColor.WHITE);
    }

    private static void processesJob(StringBuilder builder, Job job, String levelS) {
	if (job == null)
	    return;
	builder.append(job.getChatColor());
	builder.append(job.getName().replace("{level}", levelS));
	builder.append(CMIChatColor.WHITE);
    }

    /**
     * Performs player save into database
     */
    public void save() {
//	synchronized (saveLock) {
	if (!isSaved) {
	    JobsDAO dao = Jobs.getJobsDAO();
	    dao.save(this);
	    dao.saveLog(this);
	    dao.savePoints(this);
	    dao.recordPlayersLimits(this);
	    dao.updateSeen(this);
	    setSaved(true);

	    if (getPlayer() == null || !getPlayer().isOnline()) {
		Jobs.getPlayerManager().addPlayerToCache(this);
		Jobs.getPlayerManager().removePlayer(getPlayer());
	    }
	}
//	}
    }

    /**
     * Perform connect
     */
    public void onConnect() {
	isOnline = true;
    }

    /**
     * Perform disconnect for this player
     */
    public void onDisconnect() {
//	Jobs.getJobsDAO().savePoints(this);
	clearBossMaps();
	isOnline = false;
	Jobs.getPlayerManager().addPlayerToCache(this);
    }

    public void clearBossMaps() {
	for (BossBarInfo one : barMap) {
	    one.getBar().removeAll();
	    one.cancel();
	}

	barMap.clear();
    }

    /**
     * Whether or not player is online
     * @return true if online, otherwise false
     */
    public boolean isOnline() {
	return getPlayer() != null ? getPlayer().isOnline() : isOnline;
    }

    public boolean isSaved() {
	return isSaved;
    }

    public void setSaved(boolean isSaved) {
	this.isSaved = isSaved;
    }

    public Long getSeen() {
	return seen;
    }

    public void setSeen(Long seen) {
	this.seen = seen;
    }

    public HashMap<String, Boolean> getPermissionsCache() {
	return permissionsCache;
    }

    public void setPermissionsCache(HashMap<String, Boolean> permissionsCache) {
	this.permissionsCache = permissionsCache;
    }

    public void setPermissionsCache(String permission, Boolean state) {
	permissionsCache.put(permission, state);
    }

    public Long getLastPermissionUpdate() {
	return lastPermissionUpdate;
    }

    public void setLastPermissionUpdate(Long lastPermissionUpdate) {
	this.lastPermissionUpdate = lastPermissionUpdate;
    }

    /**
     * Checks whenever this player can get paid for the given action.
     * 
     * @param info {@link ActionInfo}
     * @return true if yes
     */
    public boolean canGetPaid(ActionInfo info) {
	List<JobProgression> progression = getJobProgression();
	int numjobs = progression.size();

	if (numjobs == 0) {
	    if (Jobs.getNoneJob() == null)
		return false;
	    JobInfo jobinfo = Jobs.getNoneJob().getJobInfo(info, 1);
	    if (jobinfo == null)
		return false;
	    Double income = jobinfo.getIncome(1, numjobs, maxJobsEquation);
	    Double points = jobinfo.getPoints(1, numjobs, maxJobsEquation);
	    if (income == 0D && points == 0D)
		return false;
	}

	for (JobProgression prog : progression) {
	    int level = prog.getLevel();
	    JobInfo jobinfo = prog.getJob().getJobInfo(info, level);
	    if (jobinfo == null)
		continue;

	    Double income = jobinfo.getIncome(level, numjobs, maxJobsEquation);
	    Double pointAmount = jobinfo.getPoints(level, numjobs, maxJobsEquation);
	    Double expAmount = jobinfo.getExperience(level, numjobs, maxJobsEquation);
	    if (income != 0D || pointAmount != 0D || expAmount != 0D)
		return true;
	}

	return false;
    }

    public boolean inDailyQuest(Job job, String questName) {
	HashMap<String, QuestProgression> qpl = qProgression.get(job.getName());
	if (qpl == null)
	    return false;

	for (QuestProgression one : qpl.values()) {
	    if (one.getQuest() != null && one.getQuest().getConfigName().equalsIgnoreCase(questName))
		return true;
	}

	return false;
    }

    private List<String> getQuestNameList(Job job, ActionType type) {
	List<String> ls = new ArrayList<>();
	if (!isInJob(job))
	    return ls;

	HashMap<String, QuestProgression> qpl = qProgression.get(job.getName());
	if (qpl == null)
	    return ls;

	for (QuestProgression prog : qpl.values()) {
	    if (prog.isEnded() || prog.getQuest() == null)
		continue;

	    for (HashMap<String, QuestObjective> oneAction : prog.getQuest().getObjectives().values()) {
		for (QuestObjective oneObjective : oneAction.values()) {
		    if (type == null || type.name().equals(oneObjective.getAction().name())) {
			ls.add(prog.getQuest().getConfigName().toLowerCase());
			break;
		    }
		}
	    }
	}

	return ls;
    }

    public void resetQuests(Job job) {
	resetQuests(getQuestProgressions(job));
    }

    public void resetQuests(List<QuestProgression> quests) {
	for (QuestProgression oneQ : quests) {
	    if (oneQ.getQuest() == null) {
		continue;
	    }

	    Job job = oneQ.getQuest().getJob();
	    getNewQuests(job);
	    if (qProgression.containsKey(job.getName())) {
		qProgression.remove(job.getName());
	    }
	}
    }

    public void resetQuests() {
	getJobProgression().forEach(one -> resetQuests(one.getJob()));
    }

    public void getNewQuests() {
	qProgression.clear();
    }

    public void getNewQuests(Job job) {
	java.util.Optional.ofNullable(qProgression.get(job.getName())).ifPresent(HashMap::clear);
    }

    public void replaceQuest(Quest quest) {
	HashMap<String, QuestProgression> orprog = qProgression.get(quest.getJob().getName());

	Quest q = quest.getJob().getNextQuest(getQuestNameList(quest.getJob(), null), getJobProgression(quest.getJob()).getLevel());
	if (q == null) {
	    for (JobProgression one : this.getJobProgression()) {
		if (one.getJob().isSame(quest.getJob()))
		    continue;
		q = one.getJob().getNextQuest(getQuestNameList(one.getJob(), null), getJobProgression(one.getJob()).getLevel());
		if (q != null)
		    break;
	    }
	}

	if (q == null)
	    return;

	HashMap<String, QuestProgression> prog = qProgression.get(q.getJob().getName());
	if (prog == null) {
	    prog = new HashMap<>();
	    qProgression.put(q.getJob().getName(), prog);
	}

	if (q.getConfigName().equals(quest.getConfigName()))
	    return;

	if (prog.containsKey(q.getConfigName().toLowerCase()))
	    return;

	if (q.getJob() != quest.getJob() && prog.size() >= q.getJob().getMaxDailyQuests())
	    return;

	if (orprog != null) {
	    orprog.remove(quest.getConfigName().toLowerCase());
	}
	prog.put(q.getConfigName().toLowerCase(), new QuestProgression(q));
	skippedQuests++;
    }

    public List<QuestProgression> getQuestProgressions() {
	List<QuestProgression> g = new ArrayList<>();
	for (JobProgression one : getJobProgression()) {
	    g.addAll(getQuestProgressions(one.getJob()));
	}
	return g;
    }

    public List<QuestProgression> getQuestProgressions(Job job) {
	return getQuestProgressions(job, null);
    }

    public List<QuestProgression> getQuestProgressions(Job job, ActionType type) {
	if (!isInJob(job))
	    return new ArrayList<>();

	HashMap<String, QuestProgression> g = new HashMap<>();

	if (qProgression.get(job.getName()) != null)
	    g = new HashMap<>(qProgression.get(job.getName()));

	HashMap<String, QuestProgression> tmp = new HashMap<>();
	for (Entry<String, QuestProgression> one : (new HashMap<String, QuestProgression>(g)).entrySet()) {
	    QuestProgression qp = one.getValue();

	    if (qp.isEnded()) {
		g.remove(one.getKey().toLowerCase());
		skippedQuests = 0;
	    }
	}

	if (g.size() < job.getMaxDailyQuests()) {
	    int i = 0;
	    while (i <= job.getQuests().size()) {
		++i;

		List<String> currentQuests = new ArrayList<>(g.keySet());
		Quest q = job.getNextQuest(currentQuests, getJobProgression(job).getLevel());
		if (q == null)
		    continue;

		QuestProgression qp = new QuestProgression(q);
		if (qp.getQuest() != null)
		    g.put(qp.getQuest().getConfigName().toLowerCase(), qp);

		if (g.size() >= job.getMaxDailyQuests())
		    break;
	    }
	}

	if (g.size() > job.getMaxDailyQuests()) {
	    int i = g.size();
	    while (i > 0) {
		--i;

		g.remove(g.keySet().iterator().next());

		if (g.size() <= job.getMaxDailyQuests())
		    break;
	    }
	}

	qProgression.put(job.getName(), g);

	for (QuestProgression oneJ : g.values()) {
	    Quest q = oneJ.getQuest();
	    if (q == null) {
		continue;
	    }

	    if (type == null) {
		tmp.put(q.getConfigName().toLowerCase(), oneJ);
		continue;
	    }

	    HashMap<String, QuestObjective> old = q.getObjectives().get(type);
	    if (old != null)
		for (QuestObjective one : old.values()) {
		    if (type.name().equals(one.getAction().name())) {
			tmp.put(q.getConfigName().toLowerCase(), oneJ);
			break;
		    }
		}
	}

	return tmp.values().stream().collect(Collectors.toList());
    }

    public String getQuestProgressionString() {
	String prog = "";

	for (QuestProgression one : getQuestProgressions()) {
	    Quest q = one.getQuest();
	    if (q == null || q.getObjectives().isEmpty())
		continue;

	    if (!prog.isEmpty())
		prog += ";:;";

	    prog += q.getJob().getName() + ":" + q.getConfigName() + ":" + one.getValidUntil() + ":";

	    for (HashMap<String, QuestObjective> oneA : q.getObjectives().values()) {
		for (Entry<String, QuestObjective> oneO : oneA.entrySet()) {
		    prog += oneO.getValue().getAction().toString() + ";" + oneO.getKey() + ";" + one.getAmountDone(oneO.getValue()) + ":;:";
		}
	    }

	    prog = prog.endsWith(":;:") ? prog.substring(0, prog.length() - 3) : prog;
	}

	return prog.isEmpty() ? null : prog.endsWith(";:") ? prog.substring(0, prog.length() - 2) : prog;
    }

    public void setQuestProgressionFromString(String qprog) {
	if (qprog == null || qprog.isEmpty())
	    return;

	String[] byJob = qprog.split(";:;");
	for (String one : byJob) {
	    try {
		String jname = one.split(":")[0];
		Job job = Jobs.getJob(jname);
		if (job == null)
		    continue;

		one = one.substring(jname.length() + 1);
		String qname = one.split(":")[0];
		Quest quest = job.getQuest(qname);
		if (quest == null)
		    continue;

		one = one.substring(qname.length() + 1);
		String longS = one.split(":")[0];
		Long validUntil = Long.parseLong(longS);
		one = one.substring(longS.length() + 1);

		HashMap<String, QuestProgression> currentProgression = qProgression.get(job.getName());

		if (currentProgression == null) {
		    currentProgression = new HashMap<>();
		    qProgression.put(job.getName(), currentProgression);
		}

		QuestProgression qp = currentProgression.get(qname.toLowerCase());
		if (qp == null) {
		    qp = new QuestProgression(quest);
		    qp.setValidUntil(validUntil);
		    currentProgression.put(qname.toLowerCase(), qp);
		}

		for (String oneA : one.split(":;:")) {
		    String prog = oneA.split(";")[0];
		    ActionType action = ActionType.getByName(prog);
		    if (action == null || oneA.length() < prog.length() + 1)
			continue;

		    oneA = oneA.substring(prog.length() + 1);

		    String target = oneA.split(";")[0];
		    HashMap<String, QuestObjective> old = quest.getObjectives().get(action);
		    if (old == null)
			continue;

		    QuestObjective obj = old.get(target);
		    if (obj == null)
			continue;

		    oneA = oneA.substring(target.length() + 1);
		    String doneS = oneA.split(";")[0];
		    int done = Integer.parseInt(doneS);
		    qp.setAmountDone(obj, done);
		}

		if (qp.isCompleted())
		    qp.setGivenReward(true);

	    } catch (Throwable e) {
		e.printStackTrace();
	    }
	}
    }

    public int getDoneQuests() {
	return doneQuests;
    }

    public void setDoneQuests(int doneQuests) {
	this.doneQuests = doneQuests;
    }

    private Integer questSignUpdateShed;

    public void addDoneQuest(final Job job) {
	this.doneQuests++;
	this.setSaved(false);

	if (questSignUpdateShed == null) {
	    questSignUpdateShed = Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Jobs.getInstance(), new Runnable() {
		@Override
		public void run() {
		    Jobs.getSignUtil().SignUpdate(job, SignTopType.questtoplist);
		    questSignUpdateShed = null;
		}
	    }, Jobs.getGCManager().getSavePeriod() * 60 * 20L);
	}
    }

    /**
     * @deprecated {@link Jobs#getBlockOwnerShip(BlockTypes)}
     * @return the furnace count
     */
    @Deprecated
    public int getFurnaceCount() {
	return !Jobs.getInstance().getBlockOwnerShip(BlockTypes.FURNACE).isPresent() ? 0 : Jobs.getInstance().getBlockOwnerShip(BlockTypes.FURNACE).get().getTotal(getUniqueId());
    }

    /**
     * @deprecated {@link Jobs#getBlockOwnerShip(BlockTypes)}
     * @return the brewing stand count
     */
    @Deprecated
    public int getBrewingStandCount() {
	return !Jobs.getInstance().getBlockOwnerShip(BlockTypes.BREWING_STAND).isPresent() ? 0 : Jobs.getInstance().getBlockOwnerShip(BlockTypes.BREWING_STAND).get().getTotal(getUniqueId());
    }

    /**
     * @deprecated use {@link #getMaxOwnerShipAllowed(CMIMaterial)}
     * @return max allowed brewing stands
     */
    @Deprecated
    public int getMaxBrewingStandsAllowed() {
	Double maxV = Jobs.getPermissionManager().getMaxPermission(this, "jobs.maxbrewingstands");

	if (maxV == 0)
	    maxV = (double) Jobs.getGCManager().getBrewingStandsMaxDefault();

	return maxV.intValue();
    }

    /**
     * @deprecated use {@link #getMaxOwnerShipAllowed(CMIMaterial)}
     * @return the max allowed furnaces
     */
    @Deprecated
    public int getMaxFurnacesAllowed() {
	return getMaxOwnerShipAllowed(BlockTypes.FURNACE);
    }

    /**
     * Returns the max allowed owner ship for the given block type.
     * @param type {@link BlockTypes}
     * @return max allowed owner ship
     */
    public int getMaxOwnerShipAllowed(BlockTypes type) {
	String perm = "jobs.max" + (type == BlockTypes.FURNACE
	    ? "furnaces" : type == BlockTypes.BLAST_FURNACE ? "blastfurnaces" : type == BlockTypes.SMOKER ? "smokers" : type == BlockTypes.BREWING_STAND ? "brewingstands" : "");
	if (perm.isEmpty())
	    return 0;

	Double maxV = Jobs.getPermissionManager().getMaxPermission(this, perm);

	if (maxV == 0D && type == BlockTypes.FURNACE)
	    maxV = (double) Jobs.getGCManager().getFurnacesMaxDefault();

	if (maxV == 0D && type == BlockTypes.BLAST_FURNACE)
	    maxV = (double) Jobs.getGCManager().BlastFurnacesMaxDefault;

	if (maxV == 0D && type == BlockTypes.SMOKER)
	    maxV = (double) Jobs.getGCManager().SmokersMaxDefault;

	if (maxV == 0 && type == BlockTypes.BREWING_STAND)
	    maxV = (double) Jobs.getGCManager().getBrewingStandsMaxDefault();

	return maxV.intValue();
    }

    public int getSkippedQuests() {
	return skippedQuests;
    }

    public void setSkippedQuests(int skippedQuests) {
	this.skippedQuests = skippedQuests;
    }

    public HashMap<UUID, HashMap<Job, Long>> getLeftTimes() {
	return leftTimes;
    }

    public boolean isLeftTimeEnded(Job job) {
	UUID uuid = getUniqueId();
	if (!leftTimes.containsKey(uuid))
	    return false;

	HashMap<Job, Long> map = leftTimes.get(uuid);
	return map.containsKey(job) && map.get(job).longValue() < System.currentTimeMillis();
    }

    public void setLeftTime(Job job) {
	UUID uuid = getUniqueId();

	if (leftTimes.containsKey(uuid))
	    leftTimes.remove(uuid);

	int hour = Jobs.getGCManager().jobExpiryTime;
	if (hour == 0)
	    return;

	Calendar cal = Calendar.getInstance();
	cal.setTime(new Date());

	cal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY) + hour);

	HashMap<Job, Long> map = new HashMap<>();
	map.put(job, cal.getTimeInMillis());
	leftTimes.put(uuid, map);
    }
}
