/**
 * This processor grabs the game pause times from the parsed replay.
 * The output is:
 * match_time: the game time when the pause started (in seconds)
 * pause_seconds: the duration of the pause (in seconds)
 */
function processPauseTimes(entries) {
  const pauseTimes = [];
  
  for (let i = 0; i < entries.length; i += 1) {
    const e = entries[i];
    
    if (e.type === 'game_paused' && e.key === 'pause_duration' && e.value > 0) {
      pauseTimes.push({
        match_time: e.time,
        pause_seconds: e.value,
      });
    }
  }
  
  return pauseTimes;
}

export default processPauseTimes;
