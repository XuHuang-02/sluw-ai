/* The routing page shares login cookies, never conversation memory or model answers. */
(() => {
    const toggle = document.getElementById('issueSubmissionTrial');
    const chooser = document.getElementById('routingMode');
    if (toggle && chooser) {
        const sync = () => { chooser.disabled = !toggle.checked; };
        toggle.addEventListener('change', sync); sync();
    }
    const label = mode => mode === 'CONDITIONS' ? '条件模式' : '记录模式';
    const shown = value => value == null ? '未知' : String(value);
    window.runRoutingTrial = async (question, mode, container, signal) => {
        const modes = mode === 'COMPARE' ? ['CONDITIONS', 'RECORDS'] : [mode];
        container.replaceChildren();
        const grid = document.createElement('div');
        grid.className = 'routing-results' + (modes.length === 2 ? ' compare' : '');
        container.append(grid);
        await Promise.all(modes.map(async current => {
            const card = document.createElement('section'); card.className = 'routing-result';
            const title = document.createElement('h4'); title.textContent = label(current);
            const body = document.createElement('pre'); body.textContent = '正在独立选路…';
            const metrics = document.createElement('div'); metrics.className = 'routing-metrics';
            card.append(title, body, metrics); grid.append(card);
            try {
                const response = await fetch('/api/agent/trial/evaluate', {
                    method: 'POST', credentials: 'same-origin', signal,
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify({ question, mode: current })
                });
                if (response.status === 401) throw new Error('登录已失效，请重新登录。');
                if (!response.ok) throw new Error('请求失败，HTTP ' + response.status);
                const result = await response.json();
                body.textContent = result.text || '未收到有效结果。';
                metrics.textContent = `整体 ${shown(result.elapsedMs)}ms；模型 ${shown(result.callMs)}ms；输入/输出 Token ${shown(result.inputTokens)}/${shown(result.outputTokens)}；费用未知。\n请求 ${shown(result.requestId)}；结果 ${shown(result.outcome)}；原因 ${shown(result.reason)}`;
            } catch (error) {
                body.textContent = signal.aborted ? '已停止等待；底层调用可能仍在运行。' : error.message;
            }
        }));
    };
})();
