/**
 * This processor grabs the game pause timings from the parsed replay.
 * The output is:
 * match_time: the game time when the pause started (in seconds)
 * paused_time_in_seconds: the duration of the pause (in seconds)
 */
function processPauseTimings(entries) {
  const pauseTimings = [];
  
  for (let i = 0; i < entries.length; i += 1) {
    const e = entries[i];
    
    if (e.type === 'game_paused' && e.key === 'pause_duration' && e.value > 0) {
      pauseTimings.push({
        match_time: e.time,
        paused_time_in_seconds: e.value,
      });
    }
  }
  
  return pauseTimings;
}

export default processPauseTimings;
