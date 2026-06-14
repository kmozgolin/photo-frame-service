const photoInput = document.getElementById('photoInput');
const verticalWidth = document.getElementById('verticalWidth');
const horizontalWidth = document.getElementById('horizontalWidth');
const verticalValue = document.getElementById('verticalValue');
const horizontalValue = document.getElementById('horizontalValue');
const applyButton = document.getElementById('applyButton');
const pickColorBtn = document.getElementById('pickColorBtn');
const customColorInput = document.getElementById('customColor');
const previewCanvas = document.getElementById('previewCanvas');
const downloadButton = document.getElementById('downloadButton');
const colorRadios = document.querySelectorAll('input[name="borderColor"]');

const ctx = previewCanvas.getContext('2d');
let sourceImage = null;
let selectedColor = '#000000';
let lastFrameUrl = null;

function updateValueLabels() {
  verticalValue.textContent = verticalWidth.value;
  horizontalValue.textContent = horizontalWidth.value;
}

function getSelectedColorMode() {
  const checked = document.querySelector('input[name="borderColor"]:checked');
  return checked ? checked.value : 'black';
}

function computeAutoColor(image) {
  const tempCanvas = document.createElement('canvas');
  const tempCtx = tempCanvas.getContext('2d');
  tempCanvas.width = image.width;
  tempCanvas.height = image.height;
  tempCtx.drawImage(image, 0, 0);
  const imageData = tempCtx.getImageData(0, 0, image.width, image.height).data;
  let totalLuma = 0;
  const sampleCount = Math.min(imageData.length / 4, 150000);
  const step = Math.max(1, Math.floor(imageData.length / 4 / sampleCount));

  for (let i = 0; i < imageData.length; i += step * 4) {
    const r = imageData[i];
    const g = imageData[i + 1];
    const b = imageData[i + 2];
    totalLuma += 0.299 * r + 0.587 * g + 0.114 * b;
  }

  const averageLuma = totalLuma / (imageData.length / 4 / step);
  return averageLuma > 140 ? '#000000' : '#ffffff';
}

function drawPreview() {
  if (!sourceImage) {
    ctx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
    previewCanvas.width = 1;
    previewCanvas.height = 1;
    downloadButton.disabled = true;

  }

  const borderTop = Number(verticalWidth.value);
  const borderSides = Number(horizontalWidth.value);
  const canvasWidth = sourceImage.width + borderSides * 2;
  const canvasHeight = sourceImage.height + borderTop * 2;
  previewCanvas.width = canvasWidth;
  previewCanvas.height = canvasHeight;

  const mode = getSelectedColorMode();
  let frameColor = '#000000';

  if (mode === 'black') {
    frameColor = '#000000';
  } else if (mode === 'white') {
    frameColor = '#ffffff';
  } else if (mode === 'eyedropper') {
    frameColor = selectedColor;
  } else if (mode === 'auto') {
    try {
      frameColor = computeAutoColor(sourceImage);
    } catch (error) {
      frameColor = '#000000';
    }
  }

  ctx.fillStyle = frameColor;
  ctx.fillRect(0, 0, canvasWidth, canvasHeight);
  ctx.drawImage(sourceImage, borderSides, borderTop);

  if (mode === 'eyedropper') {
    customColorInput.value = frameColor;
  }

  downloadButton.disabled = false;
  lastFrameUrl = previewCanvas.toDataURL('image/png');
}

function applyFrame() {
  drawPreview();
  if (!lastFrameUrl) return;
  downloadButton.setAttribute('data-url', lastFrameUrl);
}

function loadImageFile(file) {
  if (!file) return;
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    sourceImage = img;
    updateValueLabels();
    drawPreview();
    URL.revokeObjectURL(url);
  };
  img.onerror = () => {
    alert('Не удалось загрузить выбранный файл. Попробуйте другой файл.');
  };
  img.src = url;
}


function pickEyedropperColor() {
  if (!sourceImage) return;
  if ('EyeDropper' in window) {
    const eyeDropper = new EyeDropper();
    eyeDropper.open().then((result) => {
      selectedColor = result.sRGBHex;
      customColorInput.value = selectedColor;
      drawPreview();
    }).catch(() => {
      // отказ или отмена
    });
    return;
  }

  alert('API пипетки не поддерживается в этом браузере. Используйте цветовой селектор.');
}

photoInput.addEventListener('change', (event) => {
  const file = event.target.files[0];
  loadImageFile(file);
});

verticalWidth.addEventListener('input', () => {
  updateValueLabels();
  drawPreview();
});

horizontalWidth.addEventListener('input', () => {
  updateValueLabels();
  drawPreview();
});

colorRadios.forEach((radio) => {
  radio.addEventListener('change', () => {
    if (radio.value === 'eyedropper') {
      pickColorBtn.disabled = false;
    } else {
      pickColorBtn.disabled = true;
    }
    drawPreview();
  });
});

customColorInput.addEventListener('input', (event) => {
  selectedColor = event.target.value;
  if (getSelectedColorMode() === 'eyedropper') {
    drawPreview();
  }
});


const resultBlock = document.querySelector('.result-block');

downloadButton.addEventListener('click', () => {
  if (!lastFrameUrl) {
    alert('Сначала примените рамку, чтобы получить результат.');
    return;
  }
  const link = document.createElement('a');
  link.href = lastFrameUrl;
  link.download = 'photo-with-frame.png';
  link.click();
});

applyButton.addEventListener('click', () => {
  applyFrame();
});

downloadButton.addEventListener('click', () => {
  if (!lastFrameUrl) return;
  const link = document.createElement('a');
  link.href = lastFrameUrl;
  link.download = 'photo-with-frame.png';
  link.click();
});

function initializeState() {
  updateValueLabels();
  pickColorBtn.disabled = true;
  customColorInput.value = selectedColor;
}

initializeState();
