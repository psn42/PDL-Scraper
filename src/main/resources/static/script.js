const progressContainer = document.getElementById('progressContainer');
const progressFill = document.getElementById('progressFill');
const progressText = document.getElementById('progressText');
const downloadButton = document.getElementById('downloadButton');
let filename;
let pdfBlob;

document.getElementById('pdfForm').addEventListener('submit', function(event) {
    event.preventDefault();

    const url = document.getElementById('urlInput').value;

    document.getElementById('convertButton').disabled = true;
    progressContainer.style.display = 'block';
    downloadButton.style.display = 'none';

    const eventSource = new EventSource('/progress');
    eventSource.onmessage = function(event) {
        const message = event.data;
        const [text, currentPage, of, totalPages] = message.split(' ');
        const progressPercentage = (parseInt(currentPage) / parseInt(totalPages)) * 100;

        progressFill.style.width = progressPercentage + '%';
        progressText.innerText = message;

        if (parseInt(currentPage) === parseInt(totalPages)) {
            eventSource.close();
            progressText.innerText = 'Completed';
            downloadButton.style.display = 'block';
        }
    };

    fetch('/convert', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({ url: url })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to generate PDF');
        }
        filename = response.headers.get('Content-Disposition').split('filename=')[1];
        return response.blob();
    })
    .then(blob => {
        pdfBlob = blob;
    })
    .catch(error => {
        console.error('Error:', error);
        document.getElementById('convertButton').disabled = false;
    });
});

downloadButton.addEventListener('click', function() {
    const link = document.createElement('a');
    link.href = window.URL.createObjectURL(pdfBlob);
    link.download = filename;
    link.click();
    document.getElementById('convertButton').disabled = false;
});