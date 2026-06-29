document.addEventListener('DOMContentLoaded', () => {
    fetchFiles();
    fetchPeers();
});

const FILE_TYPE_GROUPS = {
    image: ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.heic'],
    spreadsheet: ['.xlsx', '.xls', '.csv', '.numbers'],
    package: ['.apk', '.dmg', '.exe', '.pkg', '.msi', '.ipa'],
    design: ['.psd', '.ai', '.sketch', '.fig'],
    archive: ['.zip', '.rar', '.7z', '.tar', '.gz'],
    document: ['.pdf', '.doc', '.docx', '.txt', '.md', '.rtf'],
    presentation: ['.ppt', '.pptx', '.key'],
    code: ['.json', '.xml', '.yaml', '.yml', '.sql', '.log'],
    audio: ['.mp3', '.wav', '.flac', '.aac'],
    video: ['.mp4', '.mov', '.mkv', '.avi']
};

const FILE_TYPE_BY_EXTENSION = Object.entries(FILE_TYPE_GROUPS).reduce((mapping, [type, extensions]) => {
    extensions.forEach(extension => {
        mapping[extension] = type;
    });
    return mapping;
}, {});

const FILE_TYPE_META = {
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
    if (lastDotIndex < 0) return '';
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

function createPackageFallbackIcon(type) {
    return createPackageIconSvg(type);
}

function createPackagePreview(fileName, extension) {
    const packageLabel = extension.slice(1).toUpperCase();
    const supportsDynamicIcon = ['.apk', '.exe', '.dmg', '.ipa', '.msi'].includes(extension);
    if (!supportsDynamicIcon) {
        return `
            <div class="preview-package">
                <div class="preview-package-icon preview-package-fallback">${createPackageFallbackIcon(packageLabel)}</div>
                <span class="preview-package-tag">${packageLabel}</span>
            </div>
        `;
    }

    const pkgIconUrl = `/api/pkg/icon?name=${encodeURIComponent(fileName)}`;
    return `
        <div class="preview-package">
            <div class="preview-package-icon preview-package-icon-image">
                <img class="preview-package-image js-preview-image" src="${pkgIconUrl}" alt="${fileName}" loading="lazy" />
                <div class="preview-package-fallback is-hidden">${createPackageFallbackIcon(packageLabel)}</div>
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
    const downloadUrl = `/api/download?name=${encodeURIComponent(file.name)}`;

    if (fileType === 'image') {
        return `<img class="preview-image" src="${downloadUrl}" alt="${file.name}" loading="lazy" />`;
    }

    if (extension === '.pdf' || extension === '.psd') {
        const previewUrl = `/api/file/preview?name=${encodeURIComponent(file.name)}`;
        return createPreviewImageMarkup(previewUrl, file.name, createTypedPreview(fileType, extension));
    }

    if (fileType === 'package') {
        return createPackagePreview(file.name, extension);
    }

    return createTypedPreview(fileType, extension);
}

function fetchFiles() {
    fetch('/api/files')
        .then(res => res.json())
        .then(files => {
            const gridView = document.getElementById('gridView');
            if (!files || files.length === 0) {
                gridView.innerHTML = `<div style="grid-column: 1/-1; text-align: center; color: #94a3b8; padding: 40px 0;">暂无文件，上传一个试试</div>`;
                return;
            }

            gridView.innerHTML = files.map(file => {
                const sizeMB = (file.size / 1024 / 1024).toFixed(2);
                const downloadUrl = `/api/download?name=${encodeURIComponent(file.name)}`;
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
        });
}

function fetchPeers() {
    fetch('/api/peers')
        .then(res => res.json())
        .then(peers => {
            const peerGrid = document.getElementById('peerGridView');
            if (!peers || peers.length === 0) {
                peerGrid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; color: #94a3b8; padding: 40px 0;">暂无发现同网设备</div>`;
                return;
            }

            peerGrid.innerHTML = peers.map(peer => {
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
                            <span class="media-size">最后在线：${seenAt}</span>
                            <a class="media-action" href="${peerUrl}" target="_blank" rel="noreferrer">打开设备</a>
                        </div>
                    </div>
                `;
            }).join('');
        })
        .catch(() => {
            const peerGrid = document.getElementById('peerGridView');
            if (peerGrid) {
                peerGrid.innerHTML = `<div style="grid-column: 1/-1; text-align: center; color: #94a3b8; padding: 40px 0;">设备列表加载失败</div>`;
            }
        });
}

function describeMode(mode) {
    if (mode === 'DUAL') return '双端模式';
    if (mode === 'MIXED') return '混合模式';
    return '单端模式';
}

function formatTimestamp(epochMillis) {
    if (!epochMillis) return '-';
    return new Date(epochMillis).toLocaleTimeString();
}

function bindPreviewFallbacks() {
    document.querySelectorAll('.js-preview-image').forEach(image => {
        const fallback = image.parentElement.querySelector('.preview-image-fallback, .preview-package-fallback');
        if (!fallback) return;

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

function startUpload() {
    const fileInput = document.getElementById('fileInput');
    if (fileInput.files.length === 0) return;

    const file = fileInput.files[0];
    const formData = new FormData();
    formData.append('file', file);

    const xhr = new XMLHttpRequest();
    document.getElementById('progressWrapper').style.display = 'block';
    document.getElementById('uploadBtn').disabled = true;

    xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
            const percent = Math.round((event.loaded / event.total) * 100);
            document.getElementById('progressBar').style.width = percent + '%';
            document.getElementById('percentText').innerText = percent + '%';
            document.getElementById('statusText').innerText = '正在传输...';
        }
    };

    xhr.onload = () => {
        document.getElementById('statusText').innerText = '传输完成';
        document.getElementById('uploadBtn').disabled = false;
        setTimeout(() => {
            document.getElementById('progressWrapper').style.display = 'none';
            fileInput.value = '';
            document.getElementById('uploadHint').innerText = '点击或拖拽文件到这里';
            document.getElementById('fileSizeInfo').innerText = '支持跨平台任意大文件、安装包落盘';
            fetchFiles();
        }, 1000);
    };

    xhr.open('POST', '/upload');
    xhr.send(formData);
}
