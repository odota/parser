/**
 * This processor grabs the game pause times from the parsed replay.
 * The output is:
 * time: the game time when the pause started (in seconds)
 * duration: the duration of the pause (in seconds)
 */
function processPauses(entries) {
  const pauses = [];
  
  for (let i = 0; i < entries.length; i += 1) {
    const e = entries[i];
    
    if (e.type === 'game_paused' && e.key === 'pause_duration' && e.value > 0) {
      pauses.push({
        time: e.time,
        duration: e.value,
      });
    }
  }
  
  return pauses;
}

export default processPauses;
