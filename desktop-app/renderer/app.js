'use strict';

const API_BASE = window.BACKEND_BASE || '';
const CHUNK_SIZE = 5 * 1024 * 1024;

const state = {
    files: [],
    keyword: '',
    sortKey: 'time-desc',
    transferDir: '',
    transferDirDraft: ''
};

document.addEventListener('DOMContentLoaded', () => {
    initWindowControls();
    initNav();
    initSearch();
    initSort();
    initUpload();
    initHostInfo();
    initSettings();
    fetchFiles();
    fetchPeers();
    fetchClients();
    fetchTransferDir();
    setInterval(fetchPeers, 5000);
    setInterval(fetchClients, 5000);
});

function initWindowControls() {
    const native = window.turboNative;
    if (!native) {
        return;
    }
    document.getElementById('btnMin').addEventListener('click', () => native.minimize());
    document.getElementById('btnMax').addEventListener('click', () => native.toggleMaximize());
    document.getElementById('btnClose').addEventListener('click', () => native.close());
}

function initNav() {
    document.querySelectorAll('.nav-item').forEach((item) => {
        item.addEventListener('click', () => {
            document.querySelectorAll('.nav-item').forEach((node) => node.classList.remove('active'));
            document.querySelectorAll('.view').forEach((view) => view.classList.remove('active'));
            item.classList.add('active');
            const view = document.getElementById(`view-${item.dataset.view}`);
            if (view) {
                view.classList.add('active');
            }
        });
    });
}

function initSearch() {
    const input = document.getElementById('searchInput');
    input.addEventListener('input', () => {
        state.keyword = input.value.trim().toLowerCase();
        renderFiles();
    });
}

function initSort() {
    const select = document.getElementById('sortSelect');
    select.addEventListener('change', () => {
        state.sortKey = select.value;
        renderFiles();
    });
}

async function initHostInfo() {
    const footer = document.getElementById('sidebarFooter');
    let addressHtml = '';

    if (window.turboNative && typeof window.turboNative.getNetworkAddresses === 'function') {
        try {
            const addresses = await window.turboNative.getNetworkAddresses();
            if (addresses && addresses.length > 0) {
                addressHtml = '<span class="footer-label">连接地址</span>' +
                    addresses.map(addr =>
                        `<span class="footer-addr" title="点击复制" data-addr="${addr}">${addr}</span>`
                    ).join('');
            }
        } catch (_) { /* ignore */ }
    }

    footer.innerHTML = addressHtml || '';

    footer.addEventListener('click', (e) => {
        const target = e.target.closest('.footer-addr');
        if (!target) return;
        navigator.clipboard.writeText(target.dataset.addr).then(() => {
            const original = target.textContent;
            target.textContent = '已复制';
            setTimeout(() => { target.textContent = original; }, 1200);
        });
    });
}

function setOnline(online) {
    const dot = document.getElementById('statusDot');
    const text = document.getElementById('statusText');
    dot.classList.toggle('online', online);
    dot.classList.toggle('offline', !online);
    text.textContent = online ? '在线' : '后端离线';
}

function initUpload() {
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('fileInput');
    dropZone.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', updateFileInfo);
    ['dragover', 'dragenter'].forEach((eventName) => dropZone.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropZone.classList.add('dragover');
    }));
    ['dragleave', 'dragend'].forEach((eventName) => dropZone.addEventListener(eventName, () => {
        dropZone.classList.remove('dragover');
    }));
    dropZone.addEventListener('drop', (event) => {
        event.preventDefault();
        dropZone.classList.remove('dragover');
        if (event.dataTransfer.files.length > 0) {
            fileInput.files = event.dataTransfer.files;
            updateFileInfo();
        }
    });
}

function updateFileInfo() {
    const file = document.getElementById('fileInput').files[0];
    if (!file) {
        return;
    }
    document.getElementById('uploadHint').innerText = file.name;
    document.getElementById('fileSizeInfo').innerText = `文件大小: ${(file.size / 1024 / 1024).toFixed(2)} MB`;
}

async function startUpload() {
    const fileInput = document.getElementById('fileInput');
    if (fileInput.files.length === 0) {
        return;
    }

    const file = fileInput.files[0];
    const progressWrapper = document.getElementById('progressWrapper');
    const uploadBtn = document.getElementById('uploadBtn');
    const statusText = document.getElementById('statusText2');
    const progressBar = document.getElementById('progressBar');
    const percentText = document.getElementById('percentText');

    progressWrapper.style.display = 'block';
    uploadBtn.disabled = true;
    statusText.innerText = '正在传输...';

    const total = file.size;
    const chunkCount = Math.max(1, Math.ceil(total / CHUNK_SIZE));

    try {
        let uploaded = 0;
        for (let index = 0; index < chunkCount; index += 1) {
            const start = index * CHUNK_SIZE;
            const blob = file.slice(start, Math.min(start + CHUNK_SIZE, total));

            const formData = new FormData();
            formData.append('fileName', file.name);
            formData.append('chunkIndex', String(index));
            formData.append('chunkSize', String(CHUNK_SIZE));
            formData.append('file', blob, file.name);

            const response = await fetch(`${API_BASE}/upload/chunk`, { method: 'POST', body: formData });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            uploaded += blob.size;
            const percent = Math.round((uploaded / total) * 100);
            progressBar.style.width = `${percent}%`;
            percentText.innerText = `${percent}%`;
        }

        statusText.innerText = '传输完成';
        setTimeout(() => {
            progressWrapper.style.display = 'none';
            progressBar.style.width = '0%';
            percentText.innerText = '0%';
            fileInput.value = '';
            document.getElementById('uploadHint').innerText = '点击或拖拽文件到这里';
            document.getElementById('fileSizeInfo').innerText = '支持跨平台任意大文件、安装包落盘';
            fetchFiles();
        }, 800);
    } catch (error) {
        statusText.innerText = `传输失败: ${error.message}`;
    } finally {
        uploadBtn.disabled = false;
    }
}

console.log("Preload 注入检查:", !!window.turboNative);
if (window.turboNative) {
    console.log("TurboNative 接口列表:", Object.keys(window.turboNative));
} else {
    console.error("【严重错误】Preload 脚本未正确注入，window.turboNative 为空！");
}
function initSettings() {
    const input = document.getElementById('transferDirInput');
    const chooseBtn = document.getElementById('chooseDirBtn');
    const saveBtn = document.getElementById('saveDirBtn');

    // 增加诊断：打印 window 对象的原生能力
    console.log("诊断：window.turboNative 是否存在?", !!window.turboNative);
    if (window.turboNative) {
        console.log("诊断：turboNative 可用方法:", Object.keys(window.turboNative));
    }

    const hasNativePicker = Boolean(window.turboNative && typeof window.turboNative.chooseDirectory === 'function');

    input.addEventListener('input', () => {
        state.transferDirDraft = input.value.trim();
        renderTransferDirState();
    });

    chooseBtn.disabled = !hasNativePicker;
    if (!hasNativePicker) {
        chooseBtn.title = '当前环境不支持原生目录选择 (请检查 preload.js 是否正确注入)';
    }

    chooseBtn.addEventListener('click', async () => {
        if (!hasNativePicker) {
            console.error("无法调用：chooseDirectory 方法不存在");
            return;
        }

        try {
            console.log("准备调用原生目录选择...");
            const selected = await window.turboNative.chooseDirectory();
            console.log("原生对话框返回结果:", selected);

            if (!selected) {
                console.log("用户取消了选择");
                return;
            }
            state.transferDirDraft = selected;
            input.value = selected;
            setTransferDirNote('已选择新目录，点击“保存设置”后生效。', false);
            renderTransferDirState();
        } catch (err) {
            console.error("调用 turboNative.chooseDirectory 发生异常:", err);
            setTransferDirNote('系统错误：无法打开文件夹选择器', true);
        }
    });

    saveBtn.addEventListener('click', saveTransferDir);
}

async function fetchTransferDir() {
    try {
        const response = await fetch(`${API_BASE}/api/settings/transfer-dir`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        const dir = typeof data.dir === 'string' ? data.dir : '';
        state.transferDir = dir;
        state.transferDirDraft = dir;
        document.getElementById('transferDirInput').value = dir;
        setTransferDirNote('使用 Electron 原生目录对话框选择文件传输目录。', false);
        renderTransferDirState();
    } catch (error) {
        setTransferDirNote(`目录读取失败: ${error.message}`, true);
        renderTransferDirState();
    }
}

async function saveTransferDir() {
    const target = state.transferDirDraft.trim();
    if (!target) {
        setTransferDirNote('请先选择或输入传输目录。', true);
        renderTransferDirState();
        return;
    }

    const saveBtn = document.getElementById('saveDirBtn');
    saveBtn.disabled = true;
    setTransferDirNote('正在保存传输目录...', false);

    try {
        const response = await fetch(`${API_BASE}/api/settings/transfer-dir`, {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain; charset=UTF-8' },
            body: target
        });
        if (!response.ok) {
            const message = await response.text();
            throw new Error(message || `HTTP ${response.status}`);
        }
        const data = await response.json();
        const applied = typeof data.dir === 'string' ? data.dir : target;
        state.transferDir = applied;
        state.transferDirDraft = applied;
        document.getElementById('transferDirInput').value = applied;
        setTransferDirNote('传输目录已更新。', false);
        renderTransferDirState();
        fetchFiles();
    } catch (error) {
        setTransferDirNote(`目录保存失败: ${error.message}`, true);
    } finally {
        saveBtn.disabled = false;
    }
}

function renderTransferDirState() {
    const value = document.getElementById('transferDirValue');
    const saveBtn = document.getElementById('saveDirBtn');
    const current = state.transferDir || '未配置';
    const draft = state.transferDirDraft.trim();
    const dirty = draft.length > 0 && draft !== state.transferDir;

    value.textContent = `当前生效目录: ${current}`;
    saveBtn.disabled = !dirty;
}

function setTransferDirNote(message, isError) {
    const note = document.getElementById('transferDirNote');
    note.textContent = message;
    note.classList.toggle('is-error', isError);
}

const FILE_TYPE_GROUPS = {
    image: ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.heic'],
    spreadsheet: ['.xlsx', '.xls', '.csv', '.numbers'],
    package: ['.apk', '.dmg', '.exe', '.pkg', '.msi', '.ipa'],
    design: ['.psd', '.ai', '.sketch', '.fig'],
    torrent: ['.torrent'],
    archive: ['.zip', '.rar', '.7z', '.tar', '.gz'],
    document: ['.pdf', '.doc', '.docx', '.txt', '.md', '.rtf'],
    presentation: ['.ppt', '.pptx', '.key'],
    code: ['.json', '.xml', '.yaml', '.yml', '.sql', '.log'],
    audio: ['.mp3', '.wav', '.flac', '.aac'],
    video: ['.mp4', '.mov', '.mkv', '.avi']
};

const FILE_TYPE_BY_EXTENSION = Object.entries(FILE_TYPE_GROUPS).reduce((mapping, [type, extensions]) => {
    extensions.forEach((extension) => {
        mapping[extension] = type;
    });
    return mapping;
}, {});

const FILE_TYPE_META = {
    torrent: { label: 'TORRENT', accentClass: 'accent-emerald', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3"/>' },
    spreadsheet: { label: 'SHEET', accentClass: 'accent-green', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 5.25h16.5m-16.5 4.5h16.5m-16.5 4.5h16.5m-16.5 4.5h16.5M3.75 5.25v13.5m16.5-13.5v13.5m-13.5-13.5v13.5m4.5-13.5v13.5m4.5-13.5v13.5"></path>' },
    design: { label: 'DESIGN', accentClass: 'accent-pink', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M4.5 6.75A2.25 2.25 0 016.75 4.5h10.5A2.25 2.25 0 0119.5 6.75v10.5a2.25 2.25 0 01-2.25 2.25H6.75A2.25 2.25 0 014.5 17.25V6.75Z"></path><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 15.75c1.5-3 3-4.5 4.5-4.5s2.25 1.125 3 2.25"></path><path stroke-linecap="round" stroke-linejoin="round" d="M9 9.75h.008v.008H9V9.75Z"></path>' },
    archive: { label: 'ZIP', accentClass: 'accent-amber', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z"></path>' },
    document: { label: 'DOC', accentClass: 'accent-slate', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z"></path>' },
    presentation: { label: 'PPT', accentClass: 'accent-orange', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 4.5h16.5v10.5H3.75V4.5Z"></path><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 19.5h7.5M12 15v4.5"></path><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 8.25h5.25M8.25 11.25h3.75"></path>' },
    code: { label: 'CODE', accentClass: 'accent-cyan', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M8.25 9 4.5 12l3.75 3M15.75 9l3.75 3-3.75 3M13.5 6l-3 12"></path>' },
    audio: { label: 'AUDIO', accentClass: 'accent-violet', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M9 18V6.75l9-1.5v9.75"></path><path stroke-linecap="round" stroke-linejoin="round" d="M9 15.75A2.25 2.25 0 116.75 13.5 2.25 2.25 0 019 15.75Zm9 1.5A2.25 2.25 0 1115.75 15 2.25 2.25 0 0118 17.25Z"></path>' },
    video: { label: 'VIDEO', accentClass: 'accent-red', icon: '<path stroke-linecap="round" stroke-linejoin="round" d="M15.75 10.5 19.5 8.25v7.5L15.75 13.5v4.125A2.625 2.625 0 0113.125 20.25h-7.5A2.625 2.625 0 013 17.625v-11.25A2.625 2.625 0 015.625 3.75h7.5a2.625 2.625 0 012.625 2.625V10.5Z"></path>' }
};

const PACKAGE_ICON_SVG_BY_LABEL = {
    DMG: '<path stroke-linecap="round" stroke-linejoin="round" d="M21 7.5V6a2.25 2.25 0 00-2.25-2.25H5.25A2.25 2.25 0 003 6v9.75A2.25 2.25 0 005.25 18h4.5"></path><path stroke-linecap="round" stroke-linejoin="round" d="M7.5 7.5h9M7.5 11.25h6"></path><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 15.75l1.5 1.5 3-3"></path>',
    PKG: '<path stroke-linecap="round" stroke-linejoin="round" d="M21 7.5l-9 4.5-9-4.5 9-4.5 9 4.5Z"></path><path stroke-linecap="round" stroke-linejoin="round" d="M3 7.5v9l9 4.5 9-4.5v-9"></path><path stroke-linecap="round" stroke-linejoin="round" d="M12 12v9"></path>',
    MSI: '<path stroke-linecap="round" stroke-linejoin="round" d="M4.5 6.75 12 3l7.5 3.75v10.5L12 21l-7.5-3.75V6.75Z"></path><path stroke-linecap="round" stroke-linejoin="round" d="M12 3v18M4.5 6.75 12 10.5l7.5-3.75"></path>',
    APP: '<path stroke-linecap="round" stroke-linejoin="round" d="M9 17.25v1.007a3 3 0 01-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0115 18.257V17.25m6-12V15a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 15V5.25m18 0A2.25 2.25 0 0018.75 3H5.25A2.25 2.25 0 003 5.25m18 0V12a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 12V5.25"></path>'
};

function getFileExtension(fileName) {
    const lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex < 0) {
        return '';
    }
    return fileName.slice(lastDotIndex).toLowerCase();
}

function createPreviewSvg(iconPaths, accentClass, label) {
    return `
        <div class="preview-glyph ${accentClass}">
            <svg fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" aria-label="${label}">
                ${iconPaths}
            </svg>
            <span class="preview-glyph-tag">${label}</span>
        </div>
    `;
}

function createPackageIconSvg(type) {
    return `
        <svg fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" aria-label="${type}">
            ${PACKAGE_ICON_SVG_BY_LABEL[type] || PACKAGE_ICON_SVG_BY_LABEL.APP}
        </svg>
    `;
}

function createPackagePreview(fileName, extension) {
    const packageLabel = extension.slice(1).toUpperCase();
    const supportsDynamicIcon = ['.apk', '.exe', '.dmg', '.ipa', '.msi'].includes(extension);
    if (!supportsDynamicIcon) {
        return `
            <div class="preview-package">
                <div class="preview-package-icon preview-package-fallback">${createPackageIconSvg(packageLabel)}</div>
                <span class="preview-package-tag">${packageLabel}</span>
            </div>
        `;
    }

    const pkgIconUrl = `${API_BASE}/api/pkg/icon?name=${encodeURIComponent(fileName)}`;
    return `
        <div class="preview-package">
            <div class="preview-package-icon preview-package-icon-image">
                <img class="preview-package-image js-preview-image" src="${pkgIconUrl}" alt="${fileName}" loading="lazy" />
                <div class="preview-package-fallback is-hidden">${createPackageIconSvg(packageLabel)}</div>
            </div>
            <span class="preview-package-tag">${packageLabel}</span>
        </div>
    `;
}

function createTypedPreview(fileType, extension) {
    const meta = FILE_TYPE_META[fileType] || FILE_TYPE_META.document;
    const label = fileType === 'design' ? extension.slice(1).toUpperCase() : meta.label;
    return createPreviewSvg(meta.icon, meta.accentClass, label);
}

function createPreviewImageMarkup(src, fileName, fallbackHtml) {
    return `
        <div class="preview-image-shell">
            <img class="preview-image js-preview-image" src="${src}" alt="${fileName}" loading="lazy" />
            <div class="preview-image-fallback is-hidden">${fallbackHtml}</div>
        </div>
    `;
}

function createPreviewHtml(file, extension) {
    const fileType = FILE_TYPE_BY_EXTENSION[extension] || 'document';
    const downloadUrl = `${API_BASE}/api/download?name=${encodeURIComponent(file.name)}`;

    if (fileType === 'image') {
        return `<img class="preview-image" src="${downloadUrl}" alt="${file.name}" loading="lazy" />`;
    }

    if (extension === '.pdf' || extension === '.psd') {
        const previewUrl = `${API_BASE}/api/file/preview?name=${encodeURIComponent(file.name)}`;
        return createPreviewImageMarkup(previewUrl, file.name, createTypedPreview(fileType, extension));
    }

    if (fileType === 'package') {
        return createPackagePreview(file.name, extension);
    }

    return createTypedPreview(fileType, extension);
}

function fetchFiles() {
    fetch(`${API_BASE}/api/files`)
        .then((response) => response.json())
        .then((files) => {
            setOnline(true);
            state.files = Array.isArray(files) ? files : [];
            renderFiles();
        })
        .catch(() => setOnline(false));
}

function sortFiles(files) {
    const sorted = [...files];
    const [field, dir] = state.sortKey.split('-');
    sorted.sort((a, b) => {
        let cmp = 0;
        if (field === 'name') cmp = a.name.localeCompare(b.name);
        else if (field === 'size') cmp = (a.size || 0) - (b.size || 0);
        else if (field === 'time') cmp = (a.lastModified || 0) - (b.lastModified || 0);
        return dir === 'desc' ? -cmp : cmp;
    });
    return sorted;
}

function renderFiles() {
    const gridView = document.getElementById('gridView');
    const fileCount = document.getElementById('fileCount');
    let list = state.keyword
        ? state.files.filter((file) => file.name.toLowerCase().includes(state.keyword))
        : state.files;

    list = sortFiles(list);
    if (fileCount) fileCount.textContent = `${list.length} 个文件`;

    if (!list || list.length === 0) {
        gridView.innerHTML = `<div class=”empty-tip”>${state.keyword ? '没有匹配的文件' : '暂无文件，去”发送”上传一个试试'}</div>`;
        if (fileCount) fileCount.textContent = '';
        return;
    }

    gridView.innerHTML = list.map((file) => {
        const sizeMB = (file.size / 1024 / 1024).toFixed(2);
        const downloadUrl = `${API_BASE}/api/download?name=${encodeURIComponent(file.name)}`;
        const extension = getFileExtension(file.name);
        const previewHtml = createPreviewHtml(file, extension);

        return `
            <div class="media-card">
                <div class="preview-box">${previewHtml}</div>
                <div class="media-info">
                    <span class="media-title" title="${file.name}">${file.name}</span>
                    <span class="media-size">${sizeMB} MB</span>
                    <a class="media-action" href="${downloadUrl}">下载文件</a>
                </div>
            </div>
        `;
    }).join('');

    bindPreviewFallbacks();
}

function fetchPeers() {
    fetch(`${API_BASE}/api/peers`)
        .then((response) => response.json())
        .then((peers) => {
            const peerGrid = document.getElementById('peerGridView');
            if (!peers || peers.length === 0) {
                peerGrid.innerHTML = '<div class="empty-tip">暂未发现同网设备</div>';
                return;
            }

            peerGrid.innerHTML = peers.map((peer) => {
                const modeLabel = describeMode(peer.mode);
                const seenAt = formatTimestamp(peer.lastSeen);
                const peerUrl = peer.baseUrl || `http://${peer.host}:${peer.port}/`;
                return `
                    <div class="media-card">
                        <div class="preview-box">
                            <div class="preview-glyph accent-blue">
                                <svg fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24" aria-label="${peer.nodeName}">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 4.5h15v10.5h-15V4.5Z"></path>
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 19.5h7.5M12 15v4.5"></path>
                                </svg>
                                <span class="preview-glyph-tag">${modeLabel}</span>
                            </div>
                        </div>
                        <div class="media-info">
                            <span class="media-title" title="${peer.nodeName}">${peer.nodeName}</span>
                            <span class="media-size">${peer.host}:${peer.port}</span>
                            <span class="media-size">最后在线: ${seenAt}</span>
                            <a class="media-action" href="${peerUrl}" target="_blank" rel="noreferrer">打开设备</a>
                        </div>
                    </div>
                `;
            }).join('');
        })
        .catch(() => {
            const peerGrid = document.getElementById('peerGridView');
            if (peerGrid) {
                peerGrid.innerHTML = '<div class="empty-tip">设备列表加载失败</div>';
            }
        });
}

function fetchClients() {
    fetch(`${API_BASE}/api/clients`)
        .then((response) => response.json())
        .then((clients) => {
            const grid = document.getElementById('clientGridView');
            if (!clients || clients.length === 0) {
                grid.innerHTML = '<div class="empty-tip">暂无设备连接</div>';
                return;
            }

            grid.innerHTML = clients.map((client) => {
                const seenAt = formatTimestamp(client.lastSeen);
                const parsed = parseClientDevice(client);
                return `
                    <div class="media-card">
                        <div class="preview-box">
                            <div class="preview-glyph accent-green">
                                <svg fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 17.25v1.007a3 3 0 01-.879 2.122L7.5 21h9l-.621-.621A3 3 0 0115 18.257V17.25m6-12V15a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 15V5.25m18 0A2.25 2.25 0 0018.75 3H5.25A2.25 2.25 0 003 5.25m18 0V12a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 12V5.25"></path>
                                </svg>
                                <span class="preview-glyph-tag">${parsed.tag}</span>
                            </div>
                        </div>
                        <div class="media-info">
                            <span class="media-title" title="${client.ip}">${parsed.device}</span>
                            <span class="media-size">${client.ip}</span>
                            <span class="media-size">${parsed.browser}</span>
                            <span class="media-size">请求 ${client.requestCount} 次 | ${seenAt}</span>
                        </div>
                    </div>
                `;
            }).join('');
        })
        .catch(() => {
            const grid = document.getElementById('clientGridView');
            if (grid) grid.innerHTML = '<div class="empty-tip">客户端列表加载失败</div>';
        });
}

function parseClientDevice(client) {
    const ua = client.userAgent || '';
    const model = client.deviceModel || '';
    const platform = client.platform || '';
    const platVer = client.platformVersion || '';

    let device = '未知设备';
    let browser = '未知浏览器';
    let tag = 'CLIENT';

    // --- Device ---
    if (model) {
        device = model;
    } else if (ua.includes('iPhone')) {
        device = 'iPhone';
    } else if (ua.includes('iPad')) {
        device = 'iPad';
    } else {
        const androidModel = ua.match(/;\s*([^;)]+)\s+Build\//);
        if (androidModel) device = androidModel[1].trim();
        else if (ua.includes('Android')) device = 'Android';
        else if (ua.includes('Macintosh')) device = 'Mac';
        else if (ua.includes('Windows')) device = 'Windows PC';
        else if (ua.includes('Linux') && !ua.includes('Android')) device = 'Linux';
    }

    // OS version from platform hints or UA
    let osInfo = '';
    if (platform && platVer) {
        osInfo = `${platform} ${platVer}`;
    } else {
        const iosVer = ua.match(/CPU (?:iPhone )?OS (\d+[_\d]*)/);
        const androidVer = ua.match(/Android\s+([\d.]+)/);
        const winVer = ua.match(/Windows NT ([\d.]+)/);
        const macVer = ua.match(/Mac OS X (\d+[_\d]*)/);
        if (iosVer) osInfo = 'iOS ' + iosVer[1].replace(/_/g, '.');
        else if (androidVer) osInfo = 'Android ' + androidVer[1];
        else if (winVer) osInfo = 'Windows ' + ({ '10.0': '10/11', '6.3': '8.1', '6.2': '8', '6.1': '7' }[winVer[1]] || winVer[1]);
        else if (macVer) osInfo = 'macOS ' + macVer[1].replace(/_/g, '.');
    }
    if (osInfo) device += ' (' + osInfo + ')';

    // --- Browser ---
    if (ua.includes('Electron')) { browser = 'TurboTransfer'; tag = 'APP'; }
    else if (ua.includes('Edg/')) {
        const v = ua.match(/Edg\/([\d.]+)/);
        browser = 'Edge' + (v ? ' ' + v[1].split('.')[0] : '');
    }
    else if (ua.includes('CriOS/')) {
        const v = ua.match(/CriOS\/([\d.]+)/);
        browser = 'Chrome' + (v ? ' ' + v[1].split('.')[0] : '');
    }
    else if (ua.includes('Chrome/') && !ua.includes('Edg/')) {
        const v = ua.match(/Chrome\/([\d.]+)/);
        browser = 'Chrome' + (v ? ' ' + v[1].split('.')[0] : '');
    }
    else if (ua.includes('FxiOS/') || ua.includes('Firefox/')) {
        const v = ua.match(/(?:FxiOS|Firefox)\/([\d.]+)/);
        browser = 'Firefox' + (v ? ' ' + v[1].split('.')[0] : '');
    }
    else if (ua.includes('Safari/') && !ua.includes('Chrome')) {
        const v = ua.match(/Version\/([\d.]+)/);
        browser = 'Safari' + (v ? ' ' + v[1].split('.')[0] : '');
    }
    else if (ua.includes('okhttp')) { browser = 'App (okhttp)'; tag = 'APP'; }

    if (ua.includes('Mobile')) tag = 'MOBILE';
    else if (ua.includes('iPad') || ua.includes('Tablet')) tag = 'TABLET';
    else if (ua.includes('Macintosh') || ua.includes('Windows') || ua.includes('Linux')) tag = 'PC';

    return { device, browser, tag };
}

function describeMode(mode) {
    if (mode === 'DUAL') {
        return '双端模式';
    }
    if (mode === 'MIXED') {
        return '混合模式';
    }
    return '单端模式';
}

function formatTimestamp(epochMillis) {
    if (!epochMillis) {
        return '-';
    }
    return new Date(epochMillis).toLocaleTimeString();
}

function bindPreviewFallbacks() {
    document.querySelectorAll('.js-preview-image').forEach((image) => {
        const fallback = image.parentElement.querySelector('.preview-image-fallback, .preview-package-fallback');
        if (!fallback) {
            return;
        }

        const showFallback = () => {
            image.classList.add('is-hidden');
            fallback.classList.remove('is-hidden');
        };

        image.addEventListener('error', showFallback, { once: true });
        if (image.complete && image.naturalWidth === 0) {
            showFallback();
        }
    });
}

window.startUpload = startUpload;
