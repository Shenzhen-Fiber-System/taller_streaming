from PIL import Image
import os

files = [
    r"c:\ecommerce\streaming\guion\arquitectura_streaming_es.png",
    r"c:\ecommerce\streaming\guion\arquitectura_streaming.png"
]

factor = 1.25  # Increase width by 25%

for file_path in files:
    if os.path.exists(file_path):
        try:
            img = Image.open(file_path)
            width, height = img.size
            new_width = int(width * factor)
            new_size = (new_width, height)
            
            print(f"Resizing {file_path} from {width}x{height} to {new_width}x{height}")
            
            resized_img = img.resize(new_size, Image.Resampling.LANCZOS)
            resized_img.save(file_path)
            print(f"Successfully saved {file_path}")
        except Exception as e:
            print(f"Error processing {file_path}: {e}")
    else:
        print(f"File not found: {file_path}")
